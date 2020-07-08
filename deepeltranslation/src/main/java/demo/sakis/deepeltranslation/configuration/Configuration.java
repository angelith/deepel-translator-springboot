package demo.sakis.deepeltranslation.configuration;

import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Profile("!pxs")
@EnableAspectJAutoProxy
@org.springframework.context.annotation.Configuration
public class Configuration {

	@Bean
	RestTemplate getRestClient(){
		RestTemplate restTemplate = new RestTemplate();
		final CredentialsProvider credsProvider = new BasicCredentialsProvider();
		final HttpClientBuilder clientBuilder = HttpClientBuilder.create();
		clientBuilder.setDefaultCredentialsProvider(credsProvider);
		final CloseableHttpClient client = clientBuilder.build();
		final HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
		factory.setHttpClient(client);
		restTemplate.setRequestFactory(factory);
		return restTemplate;
	}

}
