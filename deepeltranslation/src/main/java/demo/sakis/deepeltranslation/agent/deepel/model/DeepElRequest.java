package demo.sakis.deepeltranslation.agent.deepel.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeepElRequest {
	String auth_key;
	String text;
	String source_lang;
	String target_lang;
}
