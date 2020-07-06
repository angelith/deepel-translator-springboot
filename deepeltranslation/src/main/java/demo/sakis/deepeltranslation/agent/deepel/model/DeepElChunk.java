package demo.sakis.deepeltranslation.agent.deepel.model;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.util.concurrent.atomic.AtomicInteger;

@Data
@Builder
@ToString
public class DeepElChunk {
	
	String originalChunk;
	String translatedChunk;
	State state;
	AtomicInteger retriesCounter;
	int id;
	
	public void setState(State state){
		this.state = state;
	}
	
	public State getState(){
		return state;
	}
	
	public enum State {
		PENDING,
		SUBMITTED_FOR_TRANSLATION,
		DONE,
		RETRYING,
		TEMPORARY_FAILURE,
		FAILED
	}
}
