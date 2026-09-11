package com.fnphoto.tv.api;

import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.lang.reflect.Method;

import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FnHttpApiContractTest {
    @Test
    public void galleryPhotoDetail_usesCurrentWebGetOneEndpoint() throws Exception {
        Method method = FnHttpApi.class.getMethod(
                "getGalleryPhotoDetail",
                String.class,
                String.class,
                int.class
        );

        GET get = method.getAnnotation(GET.class);
        assertEquals("/p/api/v1/gallery/getOne", get.value());
        assertTrue(method.getParameters()[2].isAnnotationPresent(Query.class));
    }

    @Test
    public void searchGeoPhotos_usesCurrentWebSearchResultsEndpoint() throws Exception {
        Method method = FnHttpApi.class.getMethod(
                "searchGeoPhotos",
                String.class,
                String.class,
                okhttp3.RequestBody.class
        );

        POST post = method.getAnnotation(POST.class);
        assertEquals("/p/api/v2/search/results", post.value());
    }

    @Test
    public void geoSearchRequest_matchesCurrentWebLocationFilterBody() throws Exception {
        FnHttpApi.GeoSearchRequest request = new FnHttpApi.GeoSearchRequest("中国", "上海");

        JSONObject json = new JSONObject(new Gson().toJson(request));

        assertEquals("", json.getString("keyword"));
        assertTrue(!json.has("limit"));
        assertTrue(!json.has("offset"));
        JSONArray filters = json.getJSONArray("filters");
        assertEquals(1, filters.length());
        JSONObject filter = filters.getJSONObject(0);
        assertEquals("photo_location", filter.getString("filterName"));
        assertEquals("中国", filter.getString("filterValue"));
        JSONObject subFilter = filter.getJSONArray("subFilters").getJSONObject(0);
        assertEquals("中国", subFilter.getString("filterName"));
        assertEquals("上海", subFilter.getString("filterValue"));
    }

    @Test
    public void magicSearch_usesCurrentWebAiSearchEndpoint() throws Exception {
        Method method = FnHttpApi.class.getMethod(
                "magicSearch",
                String.class,
                String.class,
                okhttp3.RequestBody.class
        );

        POST post = method.getAnnotation(POST.class);
        assertEquals("/p/api/v1/magic-search/do", post.value());
    }

    @Test
    public void magicSearchRequest_matchesCurrentWebAiSearchBody() throws Exception {
        Class<?> requestClass = Class.forName("com.fnphoto.tv.api.FnHttpApi$MagicSearchRequest");
        Object request = requestClass.getConstructor(String.class).newInstance("美食");

        JSONObject json = new JSONObject(new Gson().toJson(request));

        assertEquals("美食", json.getString("keyword"));
        assertEquals(0, json.getJSONArray("antiFilters").length());
    }

    @Test
    public void displayBrowseEndpoints_matchCurrentWebApiContracts() throws Exception {
        Method tagsMethod = FnHttpApi.class.getMethod(
                "getTags",
                String.class,
                String.class,
                int.class,
                int.class
        );
        Method mediaCategoriesMethod = FnHttpApi.class.getMethod(
                "getMediaCategories",
                String.class,
                String.class
        );
        Method filteredSearchMethod = FnHttpApi.class.getMethod(
                "searchFilteredPhotos",
                String.class,
                String.class,
                okhttp3.RequestBody.class
        );

        assertEquals("/p/api/v1/explore/tags", tagsMethod.getAnnotation(GET.class).value());
        assertEquals("/p/api/v1/media_category/list", mediaCategoriesMethod.getAnnotation(GET.class).value());
        assertEquals("/p/api/v2/search/results", filteredSearchMethod.getAnnotation(POST.class).value());
    }

    @Test
    public void displayBrowseDtos_acceptOptionalAndAlternateServerFields() {
        Gson gson = new Gson();

        FnHttpApi.TagItem tag = gson.fromJson(
                "{\"name\":\"旅行\",\"photoCount\":3,\"posterUrl\":\"/cover.jpg\"}",
                FnHttpApi.TagItem.class
        );
        FnHttpApi.MediaCategoryItem category = gson.fromJson(
                "{\"category\":999,\"count\":0,\"id\":7,\"uuid\":\"poster\",\"title\":\"自定义\",\"fileType\":\"custom\"}",
                FnHttpApi.MediaCategoryItem.class
        );

        assertEquals(Integer.valueOf(3), tag.itemCount);
        assertEquals("/cover.jpg", tag.posterUrl);
        assertEquals(Integer.valueOf(999), category.category);
        assertEquals(Integer.valueOf(0), category.count);
        assertEquals(Integer.valueOf(7), category.id);
        assertEquals("poster", category.uuid);
        assertEquals("自定义", category.title);
        assertEquals("custom", category.fileType);
    }
}
