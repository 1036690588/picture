package com.yupi.yupicturebackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

@Service
public class UrlPictureUpload extends PictureUploadTemplate{

    @Override
    public void validPicture(Object inputSource) {
        String fileUrl = (String) inputSource;
        ThrowUtils.throwIf(fileUrl == null, ErrorCode.PARAMS_ERROR,"文件url不能为空");
        //验证url格式
        try{
            new URL(fileUrl);
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"文件地址格式不正确");
        }
        //校验url协议
        ThrowUtils.throwIf(!(fileUrl.startsWith("https://") || fileUrl.startsWith("http://")),
                ErrorCode.PARAMS_ERROR,"仅支持HTTP或HTTPS协议的文件地址");
        //发送HEAD请求以验证文件是否存在
        HttpResponse response = null;
        try{
            response = HttpUtil.createRequest(Method.HEAD, fileUrl).execute();
            //未正常返回，则无需执行其他判断
            if(response.getStatus() != HttpStatus.HTTP_OK){
                return;
            }
            //检验文件类型
            String contentType = response.header("Content-Type");
            if(StrUtil.isNotBlank(contentType)){
                final List<String> ALLOW_CONTENT_TYPE =
                        Arrays.asList("image/jpeg", "image/jpg", "image/png", "image/webp");
                ThrowUtils.throwIf(!ALLOW_CONTENT_TYPE.contains(contentType.toLowerCase()),
                        ErrorCode.PARAMS_ERROR,"文件类型错误");
            }
            //检验文件大小
            String contentLength = response.header("Content-Length");
            if(StrUtil.isNotBlank(contentLength)){
                try{
                    long length = Long.parseLong(contentLength);
                    final long TWO_M = 2 * 1024 * 1024L;
                    ThrowUtils.throwIf(length > TWO_M, ErrorCode.PARAMS_ERROR,"文件大小不能超过2M");
                }catch (NumberFormatException e){
                    throw new BusinessException(ErrorCode.PARAMS_ERROR,"文件大小格式错误");
                }
            }
        } finally {
            if(response != null){
                response.close();
            }
        }
    }

    @Override
    public void processFile(Object inputSource, File file) throws IOException {
        String fileUrl = (String) inputSource;
        HttpUtil.downloadFile(fileUrl,file);
    }

    @Override
    public String getOriginFilename(Object inputSource) {
        String fileUrl = (String) inputSource;
        return FileUtil.getName(fileUrl);
    }
}
