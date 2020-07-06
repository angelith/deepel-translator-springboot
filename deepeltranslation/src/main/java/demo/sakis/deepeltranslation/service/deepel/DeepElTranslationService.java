package demo.sakis.deepeltranslation.service.deepel;

import demo.sakis.deepeltranslation.agent.deepel.DeepElAgent;
import demo.sakis.deepeltranslation.service.AbstractTranslationService;
import org.springframework.stereotype.Service;

@Service
public class DeepElTranslationService extends AbstractTranslationService {
	DeepElTranslationService(DeepElAgent deepElAgent){
		this.translationAgent = deepElAgent;
	}
}
