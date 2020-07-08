package demo.sakis.deepeltranslation.agent.deepel.translation.engine.parallel;

import demo.sakis.deepeltranslation.agent.deepel.model.DeepElChunk;
import demo.sakis.deepeltranslation.store.ChunkStore;
import demo.sakis.deepeltranslation.agent.deepel.store.DeepElConcurrentQueueStore;
import demo.sakis.deepeltranslation.service.content.ChunkGeneratorService;
import demo.sakis.deepeltranslation.service.translation.TranslationService;
import demo.sakis.deepeltranslation.translation.engine.TranslationEngineFacade;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Component
public class TranslationParallelEngine implements TranslationEngineFacade {
	
	private final ChunkGeneratorService chunkGeneratorService;
	private final TranslationService translationService;
	
	@Value("${application.threads.use-cached-executor}")
	boolean useCachedThreads;

	@Value("${application.threads.consumer-threads}")
	private int consumerThreadNum;
	
	TranslationParallelEngine(TranslationService translationService, ChunkGeneratorService chunkGeneratorService) {
		this.translationService = translationService;
		this.chunkGeneratorService = chunkGeneratorService;
	}
	
	private static int compare(DeepElChunk o1, DeepElChunk o2) {
		return Integer.compare(o1.getId(), o2.getId());
	}
	
	private void updateChunkStore(ChunkStore<DeepElChunk> chunkStore, String content) {
		new Producer(chunkGeneratorService.generateChunks(content), chunkStore).call();
		log.debug("Producing chunks from thread " + Thread.currentThread().getId());
	}
	
	public String attemptTranslation(final String contents, String srcLang, String targetLang) {
		ThreadPoolExecutor threadPoolExecutor = null;
		try {
			if(useCachedThreads){
				log.info("Using Cached Thread Pool");
				threadPoolExecutor = (ThreadPoolExecutor) Executors.newCachedThreadPool();
			} else{
				threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(consumerThreadNum);
			}

			final ChunkStore<DeepElChunk> chunkStore = new DeepElConcurrentQueueStore();
			updateChunkStore(chunkStore, contents);
			
			CompletableFuture.allOf(IntStream.range(1, consumerThreadNum)
					.mapToObj(x -> CompletableFuture.runAsync(() -> new Consumer(chunkStore, srcLang, targetLang).call()))
					.toArray(CompletableFuture[]::new))
					.join();
			return reConstructTranslatedMessage(chunkStore);
		} finally {
			shutdownExecutor(Objects.requireNonNull(threadPoolExecutor));
		}
		
	}
	
	
	private String reConstructTranslatedMessage(ChunkStore<DeepElChunk> store) {
		List<DeepElChunk> list = new ArrayList<>();
		log.debug("Reconstructing translated chunks...");
		if (store.atLeastOneChunkFailed()) {
			return "AT LEAST ONE CHUNK FAILED - CHECK LOGS";
		}
		list = store.drainTo(list);
		try{
			return list.stream()
				.sorted(TranslationParallelEngine::compare)
				.map(DeepElChunk::getTranslatedChunk)
				.filter(Objects::nonNull)
				.collect(Collectors.joining(""));
		}
		finally {
			log.debug("Finished successfully.");
		}

	}
	
	
	public void shutdownExecutor(ThreadPoolExecutor threadPoolExecutor) {
		threadPoolExecutor.shutdown();
		try {
			threadPoolExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
			log.error(e.getLocalizedMessage(), e);
		}
	}
	
	
	@AllArgsConstructor
	public static class Producer  {
		
		private final List<DeepElChunk> chunks;
		private final ChunkStore<DeepElChunk> chunkStore;
		
		private void call() {
			try {
				chunks.forEach(x -> {
					try {
						chunkStore.putToStore(x);
					} catch (InterruptedException e) {
						log.debug(e.getLocalizedMessage(), e);
					}
				});
			} catch (Exception e) {
				log.debug(e.getLocalizedMessage(), e);
			}
		}
	}
	
	
	@AllArgsConstructor
	private class Consumer {
		
		private final ChunkStore<DeepElChunk> store;
		private final String srcLang;
		private final String targetLang;
		
		private void call() {
			DeepElChunk entry;
			try {
				log.debug("Polling - thread -> " + Thread.currentThread().getId());
				entry = store.pollStoreForElement();
				log.debug("Got something and getting to work... - thread -> " + Thread.currentThread().getId());
				while (entry != null) {
					log.debug("Retrieved entry " + System.identityHashCode(entry) + " | " + entry.getId() + " from thread " + Thread.currentThread().getId());
					switch (entry.getState()) {
						case PENDING:
							log.debug("PENDING detected for " + System.identityHashCode(entry) + " | " + entry.getId() + " from thread " + Thread.currentThread().getId());
							retrying(entry);
							break;
						case RETRYING:
							retrying(entry);
							break;
						case SUBMITTED_FOR_TRANSLATION:
							submitForTranslation(entry);
							break;
						case TEMPORARY_FAILURE:
							failed(entry);
							break;
						case FAILED:
							log.debug("FAILED detected for " + System.identityHashCode(entry) + " | " + entry.getId() + " from thread " + Thread.currentThread().getId());
							store.putToStore(entry);
							break;
						case DONE:
							log.debug("DONE detected for " + System.identityHashCode(entry) + " | " + entry.getId() + " from thread " + Thread.currentThread().getId());
							store.putToStore(entry);
							break;
						default:
							break;
					}
					if (!(store.atLeastOneChunkFailed() || store.completed())) {
						entry = store.pollStoreForElement(5L);
					} else {
						break;
					}
				}
			} catch (InterruptedException e) {
				log.error(e.getLocalizedMessage());
			}
		}
		
		private void failed(final DeepElChunk entry) throws InterruptedException {
			if (entry.getRetriesCounter().get() < 3) {
				log.debug("(failed method) Got " + System.identityHashCode(entry) + " | "
						+ " entry -> " + entry.getId()
						+ " transition: " + entry.getState() + "->" + DeepElChunk.State.RETRYING
						+ " - thread " + Thread.currentThread().getId());
				entry.getRetriesCounter().getAndIncrement();
				entry.setState(DeepElChunk.State.RETRYING);
			} else {
				log.debug("(failed method - depleted retries) Got " + System.identityHashCode(entry) + " | "
						+ " entry -> " + entry.getId()
						+ " transition: " + entry.getState() + "->" + DeepElChunk.State.FAILED
						+ " - thread " + Thread.currentThread().getId());
				entry.setState(DeepElChunk.State.FAILED);
			}
			store.putToStore(entry);
		}
		
		private void retrying(final DeepElChunk entry) throws InterruptedException {
			log.debug("(retrying method) Got " + System.identityHashCode(entry) + " | "
					+" entry -> " + entry.getId()
					+ " transition: " + entry.getState() + "->" + DeepElChunk.State.SUBMITTED_FOR_TRANSLATION
					+ " - thread " + Thread.currentThread().getId());
			Thread.sleep(1000L);
			entry.setState(DeepElChunk.State.SUBMITTED_FOR_TRANSLATION);
			store.putToStore(entry);
		}
		
		private void submitForTranslation(final DeepElChunk entry) throws InterruptedException {
			log.debug("(submit method) Got " + System.identityHashCode(entry) + " | "
					+ "  entry -> " + entry.getId()
					+ " - thread " + Thread.currentThread().getId());
			String translation;
			try {
				translation = translationService.translate(entry.getOriginalChunk(), srcLang, targetLang, RequestMethod.POST);
			} catch (Exception e) {
				log.error(e.getLocalizedMessage());
				translation = "500";
			}
			if (translation.startsWith("4") || translation.startsWith("5")) {
				log.debug("(submit method) Return code error for " + System.identityHashCode(entry) + " | "
						+ entry.getId()
						+ " transition: " + entry.getState() + "->" + DeepElChunk.State.TEMPORARY_FAILURE
						+ " - thread " + Thread.currentThread().getId());
				entry.setState(DeepElChunk.State.TEMPORARY_FAILURE);
			} else {
				entry.setTranslatedChunk(translation);
				log.debug("Successfully translated entry " + System.identityHashCode(entry) + " | "
						+ entry.getId()
						+ " transition: " + entry.getState() + "->" + DeepElChunk.State.DONE
						+ " - thread " + Thread.currentThread().getId());
				entry.setState(DeepElChunk.State.DONE);
			}
			store.putToStore(entry);
		}
	}
}


