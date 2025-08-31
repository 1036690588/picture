package com.yupi.yupicturebackend.api.sub;


import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONUtil;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class GetImagePageUrlApi {
    public static String getImagePageUrl(String imageUrl){
        Map<String, Object> hashMap = new HashMap<>();
        hashMap.put("image",imageUrl);
        hashMap.put("tc","pc");
        hashMap.put("from","pc");
        hashMap.put("image_source","PC_UPLOAD_URL");
        long currentTime = System.currentTimeMillis();
        String requestUrl = "https://graph.baidu.com/upload?uptime=" + currentTime;

        try{
            //发送请求到目标端口
            HttpResponse response = HttpRequest.post(requestUrl)
                    .form(hashMap)
                    .timeout(5000)
                    .header("acs-token", RandomUtil.randomString(10))
                    .execute();
            //判断相应状态
            if(!(response.getStatus() == HttpStatus.HTTP_OK)){
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"接口调用失败");
            }
            //解析响应
            String body = response.body();
            //处理相应结果
            Map<String,Object> responseMap = JSONUtil.toBean(body, HashMap.class);
            if(responseMap == null || !Integer.valueOf(0).equals(responseMap.get("status"))){
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"接口调用失败");
            }
            Map<String, Object> data = (Map<String, Object>) responseMap.get("data");
            String rawUrl = (String) data.get("url");
            //对url进行解码
            String resultUrl = URLUtil.decode(rawUrl, StandardCharsets.UTF_8);
            if(resultUrl == null){
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"未获取有效结果");
            }
            return resultUrl;
        } catch (RuntimeException e) {
            log.error("搜索失败",e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"搜索失败");
        }

    }

    public static void main(String[] args) {
        String imageUrl = "https://codefather.cn/logo.png";
        String resultUrl = getImagePageUrl(imageUrl);
        System.out.println(resultUrl);
    }
}
