package com.yupi.yupicturebackend.api.sub;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yupi.yupicturebackend.api.imagesearch.ImageSearchResult;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class GetImageListApi {
    /**
     * 获取图片列表
     * @param url
     * @return
     */
    public static List<ImageSearchResult> getImageList(String url){
        try{
            HttpResponse response = HttpUtil.createGet(url).execute();
            if(response.getStatus() != HttpStatus.HTTP_OK){
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"获取图片列表json失败");
            }
            String body = response.body();
            return processResponse(body);
        }catch (Exception e){
            log.error("获取图片列表失败",e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"获取图片列表失败");
        }
    }

    private static List<ImageSearchResult> processResponse(String body){
        JSONObject jsonObject = new JSONObject(body);
        if(!jsonObject.containsKey("data")){
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"未获取到图片列表");
        }
        JSONObject data = jsonObject.getJSONObject("data");
        if(!data.containsKey("list")){
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"未获取到图片列表");
        }
        JSONArray list = data.getJSONArray("list");
        return JSONUtil.toList(list,ImageSearchResult.class);
    }

    public static void main(String[] args) {
        String url = "https://www.codefather.cn/logo.png";
        String imagePageUrl = GetImagePageUrlApi.getImagePageUrl(url);
        String imageFirstUrl = GetImageFirstUrlApi.getImageFirstUrl(imagePageUrl);
        List<ImageSearchResult> imageList = getImageList(imageFirstUrl);
        System.out.println(imageList);
    }
}
