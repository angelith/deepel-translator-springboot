package demo.sakis.deepeltranslation.agent.deepel.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeepElResponse {
	
	List<Translation> translations;
	
	@Data
	public static class Translation{
		@JsonProperty("detected_source_language")
		String sourceLanguage;
		String text;
	}
}
