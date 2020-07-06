package demo.sakis.deepeltranslation.agent.deepel.thread;

import demo.sakis.deepeltranslation.agent.deepel.model.DeepElChunk;
import demo.sakis.deepeltranslation.agent.deepel.queue.ChunkStore;
import demo.sakis.deepeltranslation.agent.deepel.queue.ConcurrentQueueStore;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;

import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class Engine {
	@Getter
	Executor executor;
	ChunkStore<DeepElChunk> store;
	AtomicInteger id;
	
	Engine() {
		store = new ConcurrentQueueStore();
		executor = Executors.newCachedThreadPool();
		id = new AtomicInteger(0);
	}
	
	class Producer implements Runnable {
		
		@Override
		public void run() {
			Random random = new Random();
			for (int i = 0; i < random.nextInt(5) + 5; i++) {
				String randomString = RandomStringUtils.randomAlphabetic(10);
				DeepElChunk deepElChunk = DeepElChunk.builder()
						.originalChunk(randomString)
						.id(id.getAndIncrement())
						.state(DeepElChunk.State.PENDING)
						.retriesCounter(new AtomicInteger(0))
						.build();
				try {
					store.putToStore(deepElChunk);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	class Consumer implements Runnable {
		@Override
		public void run() {
			try {
				Random random = new Random();
				while(true){
					try {
						Thread.sleep((long) (Math.random() * 1000));
					} catch (InterruptedException e) {
						log.error(e.getLocalizedMessage(), e);
					}
					DeepElChunk entry = store.getFromStore();
					if (entry == null) {
						log.debug("Store empty -> returning");
						break;
					} else {
						if (DeepElChunk.State.DONE.equals(entry.getState())) {
							log.debug("Finished for " + entry.toString());
							if(store.isEmpty()){
								break;
							}
						} else if (DeepElChunk.State.FAILED.equals(entry.getState()) && entry.getRetriesCounter().get() < 3) {
							log.debug("Got FAILED entry -> " + entry.toString());
							entry.getRetriesCounter().getAndIncrement();
							entry.setState(DeepElChunk.State.RETRYING);
							store.putToStore(entry);
						} else if (DeepElChunk.State.FAILED.equals(entry.getState()) && entry.getRetriesCounter().get() == 3) {
							log.debug("Got FAILED entry and depleted retries -> " + entry.toString());
						} else if (DeepElChunk.State.PENDING.equals(entry.getState()) || DeepElChunk.State.RETRYING.equals(entry.getState())) {
							log.debug("Got PENDING entry -> " + entry.toString());
							entry.setState(DeepElChunk.State.SUBMITTED_FOR_TRANSLATION);
							store.putToStore(entry);
						} else if (DeepElChunk.State.SUBMITTED_FOR_TRANSLATION.equals(entry.getState())) {
							log.debug("Got SUBMITTED_FOR_TRANSLATION entry -> " + entry.toString());
							if (random.nextBoolean()) {
								log.debug("Translation successful for " + entry.getId());
								entry.setTranslatedChunk("translation -> " + entry.getOriginalChunk());
								entry.setState(DeepElChunk.State.DONE);
							} else {
								log.debug("Translation failed for " + entry.getId());
								entry.setState(DeepElChunk.State.FAILED);
							}
							store.putToStore(entry);
						}
					}
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static void main(String argc[]) {
		Engine engine = new Engine();
		ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) engine.getExecutor();
		threadPoolExecutor.submit(engine.new Producer());
		threadPoolExecutor.submit(engine.new Producer());
		threadPoolExecutor.submit(engine.new Consumer());
		threadPoolExecutor.submit(engine.new Consumer());
		threadPoolExecutor.submit(engine.new Consumer());
		threadPoolExecutor.submit(engine.new Consumer());
		
		
		
		System.out.println("Core threads: " + threadPoolExecutor.getCorePoolSize());
		System.out.println("Largest executions: "
				+ threadPoolExecutor.getLargestPoolSize());
		System.out.println("Maximum allowed threads: "
				+ threadPoolExecutor.getMaximumPoolSize());
		System.out.println("Current threads in pool: "
				+ threadPoolExecutor.getPoolSize());
		System.out.println("Currently executing threads: "
				+ threadPoolExecutor.getActiveCount());
		System.out.println("Total number of threads(ever scheduled): "
				+ threadPoolExecutor.getTaskCount());
		threadPoolExecutor.shutdown();
		try {
			threadPoolExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
			log.error(e.getLocalizedMessage(),e);
		}
	}
	
}
