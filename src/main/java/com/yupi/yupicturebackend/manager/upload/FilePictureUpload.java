package com.yupi.yupicturebackend.manager.upload;


import cn.hutool.core.io.FileUtil;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Service
public class FilePictureUpload extends PictureUploadTemplate{

    @Override
    public void validPicture(Object inputSource) {
        MultipartFile file = (MultipartFile) inputSource;
        ThrowUtils.throwIf(file == null, ErrorCode.PARAMS_ERROR,"文件不能为空");
        //校验文件大小
        long size = file.getSize();
        final long ONE_M = 1024 * 1024L;
        ThrowUtils.throwIf(size > 2 * ONE_M, ErrorCode.PARAMS_ERROR,"文件大小不能超过2M");
        //校验文件后缀
        String suffix = FileUtil.getSuffix(file.getOriginalFilename());
        //允许上传的文件后缀
        final List<String> ALLOW_FORMAT_LIST = Arrays.asList("jpg", "jpeg", "png", "webp");
        ThrowUtils.throwIf(!ALLOW_FORMAT_LIST.contains(suffix), ErrorCode.PARAMS_ERROR,"文件类型错误");
    }

    @Override
    public void processFile(Object inputSource, File file) throws IOException {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        multipartFile.transferTo(file);
    }

    @Override
    public String getOriginFilename(Object inputSource) {
        MultipartFile  multipartFile = (MultipartFile) inputSource;
        return multipartFile.getOriginalFilename();
    }
}
