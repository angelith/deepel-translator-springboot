package demo.sakis.deepeltranslation.agent.deepel.thread;

import demo.sakis.deepeltranslation.agent.deepel.model.DeepElChunk;
import demo.sakis.deepeltranslation.agent.deepel.queue.ChunkStore;
import demo.sakis.deepeltranslation.agent.deepel.queue.ConcurrentQueueStore;
import demo.sakis.deepeltranslation.service.ChunkGeneratorService;
import demo.sakis.deepeltranslation.service.TranslationService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@Slf4j
@Component
public class TranslationParallelEngine implements TranslationEngineFacade {
	
	private ChunkGeneratorService chunkGeneratorService;
	private TranslationService translationService;
	
	@Value("${application.chunks.consumer-threads}")
	private int consumerThreadNum;
	
	TranslationParallelEngine(TranslationService translationService, ChunkGeneratorService chunkGeneratorService) {
		this.translationService = translationService;
		this.chunkGeneratorService = chunkGeneratorService;
	}
	
	private static int compare(DeepElChunk o1, DeepElChunk o2) {
		if (o1.getId() == o2.getId()) {
			return 0;
		}
		if (o1.getId() < o2.getId()) {
			return -1;
		} else return 1;
	}
	
	private void updateChunkStore(ChunkStore<DeepElChunk> chunkStore, String content) {
		new Producer(chunkGeneratorService.generateChunks(content), chunkStore).call();
		log.debug("Producing chunks from thread " + Thread.currentThread().getId());
	}
	
	public String attemptTranslation(final String contents, String srcLang, String targetLang) {
		ThreadPoolExecutor threadPoolExecutor = null;
		try {
			//threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(consumerThreadNum);
			threadPoolExecutor = (ThreadPoolExecutor) Executors.newCachedThreadPool();
			final ChunkStore<DeepElChunk> chunkStore = new ConcurrentQueueStore();
			updateChunkStore(chunkStore, contents);
			
			CompletableFuture.allOf(IntStream.range(1, consumerThreadNum)
					.mapToObj(x -> CompletableFuture.runAsync(() -> new Consumer(chunkStore, srcLang, targetLang).call()))
					.toArray(CompletableFuture[]::new))
					.join();
			return reConstructTranslatedMessage(chunkStore);
		} finally {
			shutdownExecutor(threadPoolExecutor);
		}
		
	}
	
	
	private String reConstructTranslatedMessage(ChunkStore<DeepElChunk> store) {
		List<DeepElChunk> list = new ArrayList<>();
		log.debug("Reconstructing translated chunks...");
		if (store.atLeastOneChunkFailed()) {
			return "AT LEAST ONE CHUNK FAILED - CHECK LOGS";
		}
		list = store.drainTo(list);
		return list.stream()
				.sorted(TranslationParallelEngine::compare)
				.map(DeepElChunk::getTranslatedChunk)
				.filter(Objects::nonNull)
				.reduce("", String::concat);
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
	private class Producer  {
		
		List<DeepElChunk> chunks;
		ChunkStore<DeepElChunk> chunkStore;
		
		public ChunkStore<DeepElChunk> call() {
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
			return chunkStore;
		}
	}
	
	
	@AllArgsConstructor
	private class Consumer {
		
		private ChunkStore<DeepElChunk> store;
		private String srcLang;
		private String targetLang;
		
		ChunkStore<DeepElChunk> call() {
			DeepElChunk entry;
			try {
				
				log.debug("Polling - thread -> " + Thread.currentThread().getId());
				entry = store.pollStoreForElement();
				log.debug("Leaving - thread -> " + Thread.currentThread().getId());
				
				while (entry != null) {
					log.debug("Retrieved entry " + System.identityHashCode(entry) + " | " + entry.getId() + " from thread " + Thread.currentThread().getId());
					switch (entry.getState()) {
						case PENDING:
							log.debug("PENDING detected for "  + System.identityHashCode(entry) +  " | " + entry.getId() + " from thread " + Thread.currentThread().getId() );
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
							log.debug("FAILED detected for "  + System.identityHashCode(entry) +  " | " + entry.getId() + " from thread " + Thread.currentThread().getId() );
							store.putToStore(entry);
							break;
						case DONE:
							log.debug("DONE detected for "  + System.identityHashCode(entry) +  " | " + entry.getId() + " from thread " + Thread.currentThread().getId() );
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
				return store;
			}
			return store;
		}
		
		private void failed(final DeepElChunk entry) throws InterruptedException {
			if (entry.getRetriesCounter().get() < 3) {
				log.debug("(failed) Got " + System.identityHashCode(entry) + " | " + entry.getState() + " entry -> " + entry.getId() + " - thread " + Thread.currentThread().getId());
				entry.getRetriesCounter().getAndIncrement();
				entry.setState(DeepElChunk.State.RETRYING);
				store.putToStore(entry);
			} else {
				log.debug("(failed - depleted retries) Got " + System.identityHashCode(entry) + " | " + entry.getState() + " entry -> " + entry.getId() + " - thread " + Thread.currentThread().getId());
				entry.setState(DeepElChunk.State.FAILED);
				store.putToStore(entry);
			}
		}
		
		private void retrying(final DeepElChunk entry) throws InterruptedException {
			log.debug("(retrying) Got " + System.identityHashCode(entry) + " | " + entry.getState() + " entry -> " + entry.getId() + " - thread " + Thread.currentThread().getId());
			Thread.sleep(1000L);
			entry.setState(DeepElChunk.State.SUBMITTED_FOR_TRANSLATION);
			store.putToStore(entry);
		}
		
		private void submitForTranslation(final DeepElChunk entry) throws InterruptedException {
			log.debug("(submit) Got " + System.identityHashCode(entry) + " | " + entry.getState() + "  entry -> " + entry.getId() + " - thread " + Thread.currentThread().getId());
			String translation;
			try {
				translation = translationService.translate(entry.getOriginalChunk(), srcLang, targetLang, RequestMethod.POST);
			} catch (Exception e) {
				log.error(e.getLocalizedMessage());
				translation = "500";
			}
			if (translation.startsWith("4") || translation.startsWith("5")) {
				entry.setState(DeepElChunk.State.TEMPORARY_FAILURE);
				log.debug("(submit) Return code error for " + System.identityHashCode(entry) + " | " + entry.getId() + "-> thread " + Thread.currentThread().getId());
				store.putToStore(entry);
			} else {
				entry.setTranslatedChunk(translation);
				entry.setState(DeepElChunk.State.DONE);
				log.debug("Successfully translated entry " + System.identityHashCode(entry) + " | " + entry.getId() + "-> thread " + Thread.currentThread().getId());
				store.putToStore(entry);
			}
		}
	}
}


