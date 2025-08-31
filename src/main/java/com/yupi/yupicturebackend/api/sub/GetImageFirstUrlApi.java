package com.yupi.yupicturebackend.api.sub;


import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class GetImageFirstUrlApi {
    public static String getImageFirstUrl(String url){
        try{
            Document document = Jsoup.connect(url).timeout(5000).get();
            //获取document内的所有<script>标签
            Elements scriptElements = document.getElementsByTag("script");

            //遍历找到firstUrl的内容
            for (Element script : scriptElements){
                String scriptContent = script.html();
                if(scriptContent.contains("\"firstUrl\"")){
                    Pattern pattern = Pattern.compile("\"firstUrl\"\\s*:\\s*\"([^\"]+)\"");
                    Matcher matcher = pattern.matcher(scriptContent);
                    if(matcher.find()){
                        String firstUrl = matcher.group(1);
                        //处理转义字符
                        firstUrl = firstUrl.replace("\\/", "/");
                        return firstUrl;
                    }
                }
            }
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"未找到firstUrl");
        } catch (Exception e) {
            log.error("搜索失败",e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"搜索失败");
        }
    }

    public static void main(String[] args) {
        String url = "https://www.codefather.cn/logo.png";
        String imagePageUrl = GetImagePageUrlApi.getImagePageUrl(url);
        String imageFirstUrl = getImageFirstUrl(imagePageUrl);
        System.out.println(imageFirstUrl);
    }
}
