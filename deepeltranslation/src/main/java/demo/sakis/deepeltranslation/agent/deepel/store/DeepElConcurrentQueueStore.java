package demo.sakis.deepeltranslation.agent.deepel.store;

import demo.sakis.deepeltranslation.agent.deepel.model.DeepElChunk;
import demo.sakis.deepeltranslation.store.ChunkStore;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
public class DeepElConcurrentQueueStore implements ChunkStore<DeepElChunk> {
	
	private final LinkedBlockingQueue<DeepElChunk> storeQueue = new LinkedBlockingQueue<>();
	
	@Override
	public synchronized boolean completed() {
		return storeQueue.stream()
				.allMatch(x -> DeepElChunk.State.DONE.equals(x.getState()));
	}
	
	@Override
	public synchronized boolean atLeastOneChunkFailed() {
		return storeQueue.stream()
				.anyMatch(x -> DeepElChunk.State.FAILED.equals(x.getState()));
	}
	
	@Override
	public List<DeepElChunk> drainTo(List<DeepElChunk> output) {
		storeQueue.drainTo(output);
		return output;
	}
	
	@Override
	public DeepElChunk peek() {
		return storeQueue.peek();
	}
	
	@Override
	public DeepElChunk getFromStore() {
		return storeQueue.poll();
	}
	
	@Override
	public DeepElChunk pollStoreForElement() throws InterruptedException {
		return storeQueue.take();
	}
	
	@Override
	public DeepElChunk pollStoreForElement(final Long waitTime) throws InterruptedException {
		return storeQueue.poll(waitTime, TimeUnit.SECONDS);
	}
	
	@Override
	public void putToStore(final DeepElChunk input) throws InterruptedException {
		storeQueue.put(input);
	}
	
	@Override
	public boolean isEmpty() {
		return storeQueue.isEmpty();
	}
}
