package demo.sakis.deepeltranslation.store;

import java.util.List;

public interface ChunkStore<T> {
	boolean  completed();
	
	boolean atLeastOneChunkFailed();
	
	List<T> drainTo(List<T> output);
	
	T peek();
	
	T getFromStore();
	
	T pollStoreForElement() throws InterruptedException;
	
	T pollStoreForElement(Long waitTime) throws InterruptedException;
	
	void putToStore(T input) throws InterruptedException;
	
	boolean isEmpty();
	
}
