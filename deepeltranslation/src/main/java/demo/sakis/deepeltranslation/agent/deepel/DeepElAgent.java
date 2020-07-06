package demo.sakis.deepeltranslation.agent.deepel;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo.sakis.deepeltranslation.agent.TranslationAgent;
import demo.sakis.deepeltranslation.agent.deepel.model.DeepElChunk;
import demo.sakis.deepeltranslation.agent.deepel.model.DeepElRequest;
import demo.sakis.deepeltranslation.agent.deepel.model.DeepElResponse;
import demo.sakis.deepeltranslation.agent.deepel.queue.ChunkStore;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
public class DeepElAgent implements TranslationAgent<String> {
	
	private final RestTemplate restTemplate;
	
	@Value("${deepEl-connector.api.host}")
	String host;
	
	@Value("${deepEl-connector.api.scheme}")
	String scheme;
	
	@Value("${deepEl-connector.api.path}")
	String path;
	
	@Value("${deepEl-connector.api.get-query-string}")
	String queryString;
	
	@Value("${deepEl-connector.api.post-query-string}")
	String postQueryString;
	
	@Value("${deepEl-connector.api.auth-key}")
	String authKey;
	
	
	DeepElAgent(RestTemplate restTemplate){
		this.restTemplate = restTemplate;
	}
	
	@Override
	public String translate(final String inputText, final String srcLang, final String destLang, final RequestMethod requestMethod) {
		DeepElRequest deepElRequest = DeepElRequest.builder()
				.target_lang(destLang)
				.source_lang(srcLang)
				.text(inputText)
				.auth_key(authKey)
				.build();
		
		if(RequestMethod.GET.equals(requestMethod)){
			return translateGet(deepElRequest);
		}
		else if(RequestMethod.POST.equals(requestMethod)){
			return translatePost(deepElRequest);
		}
	else return "HTTP method not supported!";
	}
	
	private String translateGet(final DeepElRequest deepElRequest) {
		ObjectMapper objectMapper = new ObjectMapper();
		Map<String,Object> objMap = objectMapper.convertValue(deepElRequest, Map.class);
		
		UriComponents uriComponents = UriComponentsBuilder.newInstance()
				.host(host)
				.scheme(scheme)
				.path(path)
				.query(queryString)
				.buildAndExpand(objMap);
		
		ResponseEntity<DeepElResponse> deepElResponseResponseEntity = restTemplate.getForEntity(uriComponents.encode().toUri(), DeepElResponse.class);
		return Optional.ofNullable(deepElResponseResponseEntity.getBody().getTranslations().get(0).getText()).orElse("Nothing returned. Check logs.");
	}
	
	private MultiValueMap<String,Object> covertObjectToMultiValueMap(Object o){
		MultiValueMap<String, Object> multiValueMap = new LinkedMultiValueMap<>();
		
		Arrays.stream(ReflectionUtils.getDeclaredMethods(o.getClass()))
				.forEach(method -> {
					if(method.getName().startsWith("get") && Modifier.isPublic(method.getModifiers())){
						String methodName = method.getName();
						String fieldName = StringUtils.substringAfter(methodName, "get").toLowerCase();
						try {
							multiValueMap.put(fieldName, Arrays.asList(method.invoke(o)));
						} catch (IllegalAccessException | InvocationTargetException e) {
							log.error(e.getMessage(), e);
						}
					}
				});
		return multiValueMap;
	}
	
	private String translatePost(final DeepElRequest deepElRequest) {
		UriComponents uriComponents = UriComponentsBuilder.newInstance()
				.host(host)
				.scheme(scheme)
				.path(path)
				.query(postQueryString)
				.buildAndExpand(deepElRequest.getAuth_key());
		
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		HttpEntity<MultiValueMap<String,Object>> request = new HttpEntity<>(covertObjectToMultiValueMap(deepElRequest), headers);
		ResponseEntity<DeepElResponse> deepElResponseEntity = restTemplate.postForEntity(uriComponents.encode().toUri(), request, DeepElResponse.class);
		if(deepElResponseEntity.getStatusCode() != HttpStatus.OK){
			return String.valueOf(deepElResponseEntity.getStatusCode().value());
		}
		return Optional.ofNullable(deepElResponseEntity.getBody().getTranslations().get(0).getText()).orElse("Nothing returned. Check logs.");
	}
	
	@Override
	public Collection<String> translate(final Collection<String> collection, final String srcLang, final String destLang, final RequestMethod requestMethod) {
		return collection.stream()
				.peek(System.out::println)
				.map(s -> "Translated by DeepEl: " + s)
				.collect(Collectors.toList());
	}
	
}
