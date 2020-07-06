package demo.sakis.deepeltranslation.controller;

import demo.sakis.deepeltranslation.agent.deepel.thread.TranslationEngineFacade;
import demo.sakis.deepeltranslation.agent.deepel.thread.TranslationParallelEngine;

import org.apache.logging.log4j.util.Strings;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/translation")
public class TranslationController {
	
	
	private TranslationEngineFacade translationParallelEngine;
	
	TranslationController(TranslationParallelEngine translationParallelEngine){
		this.translationParallelEngine = translationParallelEngine;
	}
	
	@GetMapping("/big-text")
	public String attemptBigTranslation(@RequestParam(value = "srcLang")String srcLang,
	                                    @RequestParam("dstLang") String dtsLang) {
		String contents = Strings.EMPTY;
		try(InputStream inputStream = this.getClass().getResourceAsStream("/over30kbcontent.txt")){
			BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
			contents = new String(bufferedInputStream.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return  translationParallelEngine.attemptTranslation(contents, srcLang, dtsLang);
	}
	
	@GetMapping("/simple-text")
	public String get(@RequestParam(value = "text") String text,
	                  @RequestParam(value = "srcLang")String srcLang,
	                  @RequestParam("dstLang") String dtsLang) {
		
		return  translationParallelEngine.attemptTranslation(text, srcLang, dtsLang);
	}
	/*
	@GetMapping("/collection")
	public List<String> get(@RequestParam("texts") List<String> texts,
	                        @RequestParam("srcLang") String srcLang,
	                        @RequestParam("dstLang") String dtsLang){
		return Collections.unmodifiableList((List<String>) translationService.translate(texts, srcLang, dtsLang, RequestMethod.GET));
	}*/
	
	
}
