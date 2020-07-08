package demo.sakis.deepeltranslation.service.translation;


import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Collection;

public interface TranslationService {
	
	 <R> R translate(@NonNull R input, @NonNull String srcLang, @NonNull String destLang, final RequestMethod requestMethod);
	
	 <R> Collection<R> translate(@NonNull Collection<R> collection, @NonNull String srcLang, @NonNull String destLang, final RequestMethod requestMethod);
}
