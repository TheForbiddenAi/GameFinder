package me.theforbiddenai.gamefinder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestHelper {

    /**
     * Checks if two collections are equal. Ignores order
     *
     * @param expected Expected collection
     * @param actual   Actual Collection
     * @param <T>      Type of the objects in each collection
     */
    public static <T> void assertCollectionEquals(Collection<T> expected, Collection<T> actual) {
        assertEquals(expected.size(), actual.size());
        assertTrue(expected.containsAll(actual));
        assertTrue(actual.containsAll(expected));
    }

    /**
     * Creates and setups all the mock calls for a mock OkHttpClient object
     *
     * @param byteStream The InputStream to return when the byteStream is called on the mockResponseBody object
     * @return A mock OkHttpClient object
     * @throws IOException This will never happen
     */
    public static OkHttpClient setupOkHttpMocks(InputStream byteStream) throws IOException {
        OkHttpClient mockHttpClient = mock(OkHttpClient.class);

        Call mockCall = Mockito.mock(Call.class);
        Response mockResponse = Mockito.mock(Response.class);
        ResponseBody mockResponseBody = Mockito.mock(ResponseBody.class);

        when(mockHttpClient.newCall(Mockito.any())).thenReturn(mockCall);
        when(mockCall.execute()).thenReturn(mockResponse);

        when(mockResponse.body()).thenReturn(mockResponseBody);
        when(mockResponse.isSuccessful()).thenReturn(true);

        when(mockResponseBody.string()).thenReturn("");
        when(mockResponseBody.byteStream()).thenReturn(byteStream);

        return mockHttpClient;
    }

    /**
     * Creates and setups all the mock calls for a mock ObjectMapper object
     *
     * @param callable The callback that determines the JsonNode that is returned based on the url path
     * @return A mock ObjectMapper object
     */
    public static ObjectMapper createMockObjectMapper(ObjectMapperCallable callable) {
        return mock(ObjectMapper.class, answer -> {
            // Make sure the method being called is readTree, if not do not inject return values
            if (!answer.getMethod().getName().equals("readTree")) return answer.callRealMethod();

            // Ensure the argument passed is a URL object
            Object arg = answer.getArgument(0);
            if (!(arg instanceof URL url)) return answer.callRealMethod();

            // Return the correct value depending on the URL path
            JsonNode jsonNode = callable.processURL(url.getPath());
            if (jsonNode != null) return jsonNode;

            return answer.callRealMethod();
        });
    }

    public interface ObjectMapperCallable {

        JsonNode processURL(String urlPath);

    }

}
