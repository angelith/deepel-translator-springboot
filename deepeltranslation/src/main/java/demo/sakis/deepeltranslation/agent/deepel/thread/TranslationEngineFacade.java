package demo.sakis.deepeltranslation.agent.deepel.thread;

import java.util.concurrent.ThreadPoolExecutor;

public interface TranslationEngineFacade {
	
	String attemptTranslation(final String contents,final String srcLang, final String targetLang);
	
	void shutdownExecutor(ThreadPoolExecutor threadPoolExecutor);
	
}
