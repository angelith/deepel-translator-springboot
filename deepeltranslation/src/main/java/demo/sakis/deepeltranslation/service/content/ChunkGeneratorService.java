package demo.sakis.deepeltranslation.service.content;

import demo.sakis.deepeltranslation.agent.deepel.model.DeepElChunk;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public interface ChunkGeneratorService {

	List<DeepElChunk> generateChunks(String text);
	
	default int chunkKbMaxSize(){
		return 30 * 1024;
	}
}
