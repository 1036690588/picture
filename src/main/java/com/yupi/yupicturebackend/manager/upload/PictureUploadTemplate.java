package com.yupi.yupicturebackend.manager.upload;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.yupi.yupicturebackend.config.CosClientConfig;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.manager.CosManager;
import com.yupi.yupicturebackend.model.dto.picture.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.Date;

@Slf4j
public abstract class PictureUploadTemplate {
    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private CosManager cosManager;

    /**
     * 模板方法，上传图片
     * @param inputSource
     * @param uploadPathPrefix
     * @return
     */
    public UploadPictureResult uploadPicture(Object inputSource, String uploadPathPrefix){
        validPicture(inputSource);
        //图片上传地址
        String uuid = RandomUtil.randomString(16);
        String filename = getOriginFilename(inputSource);
        String formatName = String.format("%s_%s.%s", DateUtil.formatDate(new Date()),uuid,
                FileUtil.getSuffix(filename));
        String uploadPath = String.format("%s/%s",uploadPathPrefix,formatName);
        File tempFile = null;
        //创建临时文件
        try{
            tempFile = File.createTempFile(uploadPath, null);
            //处理文件来源（本地或URL）
            processFile(inputSource, tempFile);
            //上传图片到对象存储
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, tempFile);
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            //封装返回结果
            return buildResult(filename, uploadPath, tempFile, imageInfo);
        } catch (Exception e) {
            log.error("file upload error",e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败");
        }finally {
            deleteTempPicture(tempFile);
        }

    }

    /**
     * 校验输入源
     * @param inputSource
     */
    public abstract void validPicture(Object inputSource);

    /**
     * 处理输入源并生成本地的临时文件
     * @param inputSource
     * @param file
     */
    public abstract void processFile(Object inputSource, File file) throws IOException;

    /**
     * 获取输入源的原始文件名
     * @param inputSource
     * @return
     */
    public abstract String getOriginFilename(Object inputSource);

    /**
     * 封装返回结果
     * @param filename 原始文件名
     * @param uploadPath 上传路径
     * @param file 本地临时文件
     * @param imageInfo 图片信息
     * @return
     */
    public UploadPictureResult buildResult(String filename,String uploadPath,File file,ImageInfo imageInfo){
        //封装返回结果
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        uploadPictureResult.setUrl(String.format("%s/%s",cosClientConfig.getHost(),uploadPath));
        uploadPictureResult.setPicName(FileUtil.mainName(filename));
        uploadPictureResult.setPicSize(FileUtil.size(file));
        uploadPictureResult.setPicWidth(imageInfo.getWidth());
        uploadPictureResult.setPicHeight(imageInfo.getHeight());
        Double scale = NumberUtil.round(imageInfo.getWidth() * 1.0 / imageInfo.getHeight(), 2).doubleValue();
        uploadPictureResult.setPicScale(scale);
        uploadPictureResult.setPicFormat(imageInfo.getFormat());
        return uploadPictureResult;
    }



    /**
     * 删除临时文件
     * @param file
     */
    public void deleteTempPicture(File file){
        if(file == null){
            return ;
        }
        boolean delete = file.delete();
        if(!delete){
            log.error("file delete error, filePath = "+ file.getPath());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件删除失败");
        }
    }
}
