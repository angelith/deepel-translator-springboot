package demo.sakis.deepeltranslation.agent;


import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Collection;

public interface TranslationAgent<R> {

     R translate(R inputText, String srcLang, String destLang, final RequestMethod requestMethod);
	
	 Collection<R> translate(Collection<R> collection, String srcLang, String destLang, final RequestMethod requestMethod);
}
