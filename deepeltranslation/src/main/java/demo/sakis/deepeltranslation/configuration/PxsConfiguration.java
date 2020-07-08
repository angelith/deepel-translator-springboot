package demo.sakis.deepeltranslation.configuration;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.ProxyAuthenticationStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Profile("pxs")
@EnableAspectJAutoProxy
@org.springframework.context.annotation.Configuration
public class PxsConfiguration {
	
	@Value("${httpClient.proxy.host}")
	String proxyHost;
	@Value("${httpClient.proxy.port}")
	String proxyPort;
	@Value("${httpClient.proxy.credentials.user}")
	String proxyUser;
	@Value("${httpClient.proxy.credentials.password}")
	String proxyPassword;
	
	@Bean
	RestTemplate getRestClient(){
		RestTemplate restTemplate = new RestTemplate();
		final int proxyPortNum = Integer.parseInt(proxyPort);
		final CredentialsProvider credsProvider = new BasicCredentialsProvider();
		final HttpClientBuilder clientBuilder = HttpClientBuilder.create();
		if(proxyHost != null){
			credsProvider.setCredentials(new AuthScope(proxyHost, proxyPortNum), new UsernamePasswordCredentials(proxyUser, proxyPassword));
			clientBuilder.setProxy(new HttpHost(proxyHost, proxyPortNum));
			clientBuilder.setProxyAuthenticationStrategy(new ProxyAuthenticationStrategy());
		}
		clientBuilder.setDefaultCredentialsProvider(credsProvider);
		final CloseableHttpClient client = clientBuilder.build();
		final HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
		factory.setHttpClient(client);
		restTemplate.setRequestFactory(factory);
		return restTemplate;
	}

}
