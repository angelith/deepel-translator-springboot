package demo.sakis.deepeltranslation.service.translation;

import demo.sakis.deepeltranslation.agent.TranslationAgent;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Collection;

public abstract class AbstractTranslationService implements TranslationService {
	
	protected TranslationAgent translationAgent;

	@Override
	public <R> R translate(final R input, String srcLang, String destLang, final RequestMethod requestMethod) {
		return (R) translationAgent.translate(input, srcLang, destLang, requestMethod);
	}
	
	@Override
	public <R> Collection<R> translate(final Collection<R> collection, String srcLang, String destLang, final RequestMethod requestMethod) {
		return translationAgent.translate(collection, srcLang, destLang, requestMethod);
	}
}
