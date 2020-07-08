package demo.sakis.deepeltranslation.agent.deepel.service.content;

import demo.sakis.deepeltranslation.agent.deepel.model.DeepElChunk;
import demo.sakis.deepeltranslation.service.content.ChunkGeneratorService;
import org.springframework.boot.web.servlet.server.Encoding;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static demo.sakis.deepeltranslation.util.TranslationHelper.splitStringByByteLength;

@Service
public class DeepElChunkGeneratorServiceDefaultImpl implements ChunkGeneratorService {
	
	private static final AtomicInteger id = new AtomicInteger(0);
	
	@Override
	public List<DeepElChunk> generateChunks(final String text) {
		if (text.getBytes().length < chunkKbMaxSize()) {
			return Collections.singletonList(DeepElChunk.builder()
					.originalChunk(text)
					.state(DeepElChunk.State.PENDING)
					.retriesCounter(new AtomicInteger(0))
					.id(1)
					.build());
		} else {
			String[] chunks = splitStringByByteLength(text, Encoding.DEFAULT_CHARSET.name(), chunkKbMaxSize());
			List<DeepElChunk> result;
			synchronized (id) {
				result = Arrays.stream(chunks)
						.map(x -> DeepElChunk.builder()
								.originalChunk(x)
								.state(DeepElChunk.State.PENDING)
								.id(id.getAndIncrement())
								.retriesCounter(new AtomicInteger(0))
								.build())
						.collect(Collectors.toList());
				id.set(0);
			}
			return result;
		}
	}
}
