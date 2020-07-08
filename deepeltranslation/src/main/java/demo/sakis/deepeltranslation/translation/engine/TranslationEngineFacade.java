package demo.sakis.deepeltranslation.translation.engine;

import java.util.concurrent.ThreadPoolExecutor;

public interface TranslationEngineFacade {
	
	String attemptTranslation(final String contents,final String srcLang, final String targetLang);
	
}
