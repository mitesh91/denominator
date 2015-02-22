package denominator.clouddns;

import com.squareup.okhttp.mockwebserver.MockResponse;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import dagger.Module;
import dagger.Provides;
import denominator.Credentials;
import denominator.Credentials.ListCredentials;
import denominator.DNSApi;
import denominator.Denominator;

import static denominator.CredentialsConfiguration.credentials;

@Test(singleThreaded = true)
public class CloudDNSProviderDynamicUpdateMockTest {

  MockCloudDNSServer server;

  @Test
  public void dynamicEndpointUpdates() throws Exception {
    final AtomicReference<String> url = new AtomicReference<String>(server.url());
    server.enqueueAuthResponse();
    server.enqueue(new MockResponse().setResponseCode(404).setBody(
        "{\"message\":\"Not Found\",\"code\":404,\"details\":\"\"}"));

    DNSApi api = Denominator.create(new CloudDNSProvider() {
      @Override
      public String url() {
        return url.get();
      }
    }, credentials(server.credentials())).api();

    api.zones().iterator();
    server.assertAuthRequest();
    server.assertRequest();

    MockCloudDNSServer server2 = new MockCloudDNSServer();
    url.set(server2.url());
    server2.enqueueAuthResponse();
    server2.enqueue(new MockResponse().setBody("{ \"domains\": [] }"));

    api.zones().iterator();

    server2.assertAuthRequest();
    server2.assertRequest();
    server2.shutdown();
  }

  @Test
  public void dynamicCredentialUpdates() throws Exception {
    server.enqueueAuthResponse();
    server.enqueue(new MockResponse().setResponseCode(404).setBody(
        "{\"message\":\"Not Found\",\"code\":404,\"details\":\"\"}"));

    AtomicReference<Credentials>
        dynamicCredentials =
        new AtomicReference<Credentials>(server.credentials());

    DNSApi
        api =
        Denominator.create(server, new OverrideCredentials(dynamicCredentials)).api();

    api.zones().iterator();

    server.assertAuthRequest();
    server.assertRequest();

    dynamicCredentials.set(ListCredentials.from("jclouds-bob", "comeon"));

    server.credentials("jclouds-bob", "comeon");
    server.enqueueAuthResponse();
    server.enqueue(new MockResponse().setResponseCode(404).setBody(
        "{\"message\":\"Not Found\",\"code\":404,\"details\":\"\"}"));

    api.zones().iterator();

    server.assertAuthRequest();
    server.assertRequest();
  }

  @Module(complete = false, library = true, overrides = true)
  static class OverrideCredentials {

    final AtomicReference<Credentials> dynamicCredentials;

    OverrideCredentials(AtomicReference<Credentials> dynamicCredentials) {
      this.dynamicCredentials = dynamicCredentials;
    }

    @Provides
    public Credentials get() {
      return dynamicCredentials.get();
    }
  }

  @BeforeMethod
  public void resetServer() throws IOException {
    server = new MockCloudDNSServer();
  }

  @AfterMethod
  public void shutdownServer() throws IOException {
    server.shutdown();
  }
}
