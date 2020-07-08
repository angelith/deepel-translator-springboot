package demo.sakis.deepeltranslation.agent.deepel.service.translation;

import demo.sakis.deepeltranslation.agent.deepel.DeepElAgent;
import demo.sakis.deepeltranslation.service.translation.AbstractTranslationService;
import org.springframework.stereotype.Service;

@Service
public class DeepElTranslationService extends AbstractTranslationService {
	DeepElTranslationService(DeepElAgent deepElAgent){
		this.translationAgent = deepElAgent;
	}
}
