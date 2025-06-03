package com.yupi.yupicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.manager.upload.FilePictureUpload;
import com.yupi.yupicturebackend.manager.upload.PictureUploadTemplate;
import com.yupi.yupicturebackend.manager.upload.UrlPictureUpload;
import com.yupi.yupicturebackend.mapper.PictureMapper;
import com.yupi.yupicturebackend.model.dto.file.PictureUploadRequest;
import com.yupi.yupicturebackend.model.dto.picture.PictureQueryRequest;
import com.yupi.yupicturebackend.model.dto.picture.PictureReviewRequest;
import com.yupi.yupicturebackend.model.dto.picture.PictureUploadByBatchRequest;
import com.yupi.yupicturebackend.model.dto.picture.UploadPictureResult;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.PictureReviewStatusEnum;
import com.yupi.yupicturebackend.model.enums.UserRoleEnum;
import com.yupi.yupicturebackend.model.vo.PictureVO;
import com.yupi.yupicturebackend.model.vo.UserVO;
import com.yupi.yupicturebackend.service.PictureService;
import com.yupi.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
* @author admin
* @description 针对表【picture(图片)】的数据库操作Service实现
* @createDate 2025-05-19 10:33:11
*/
@Service
@Slf4j
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
    implements PictureService{

//    @Resource
//    private FileManager fileManager;
    @Resource
    private FilePictureUpload filePictureUpload;

    @Resource
    private UrlPictureUpload urlPictureUpload;

    @Resource
    private UserService userService;

    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        //用于判断是否是新增还是更新图片
        ThrowUtils.throwIf(pictureUploadRequest == null, ErrorCode.PARAMS_ERROR);
        Long pictureId = pictureUploadRequest.getId();
        if(pictureId != null){
            Picture picture = this.baseMapper.selectById(pictureId);
            //如果图片不是当前用户上传的，或者不是管理员，则不能更新图片
            if(!loginUser.getId().equals(picture.getUserId())
                    && !UserRoleEnum.ADMIN.getValue().equals(loginUser.getUserRole())){
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
            //如果是更新图片，需要校验图片是否存在
            ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR,"图片不存在");
        }
        //上传图片得到信息
        //按照用户id划分目录
        String uploadPathPrefix = String.format("public/%s",loginUser.getId());
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        if(inputSource instanceof String){
            pictureUploadTemplate = urlPictureUpload;
        }
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);
        //构造要入库的图片信息
        Picture picture = new Picture();
        picture.setUrl(uploadPictureResult.getUrl());
        String picName = uploadPictureResult.getPicName();

        if(StrUtil.isNotBlank(pictureUploadRequest.getPicName())){
            picName = pictureUploadRequest.getPicName();
        }
        picture.setName(picName);
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
        picture.setUserId(loginUser.getId());
        //补充审核参数
        this.fillReviewParams(picture, loginUser);
        //如果pictureId不为空，表示更新，否则是新增
        if(pictureId != null){
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }
        boolean result = this.saveOrUpdate(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR,"图片上传失败");
        return PictureVO.objToVo(picture);
    }

    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        ThrowUtils.throwIf(pictureQueryRequest == null, ErrorCode.PARAMS_ERROR);
        //从对象中取值
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();

        QueryWrapper<Picture> pictureQueryWrapper = new QueryWrapper<>();
        if(StrUtil.isNotBlank(searchText)){
            pictureQueryWrapper.and(qw -> qw.like("name",searchText)
                    .or().like("introduction",searchText));
        }
        pictureQueryWrapper.eq(ObjUtil.isNotEmpty(id) , "id", id);
        pictureQueryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        pictureQueryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        pictureQueryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        pictureQueryWrapper.eq(ObjUtil.isNotEmpty(picHeight) , "picHeight", picHeight);
        pictureQueryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        pictureQueryWrapper.eq(StrUtil.isNotBlank(category),"category",category);
        pictureQueryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        pictureQueryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        pictureQueryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        pictureQueryWrapper.eq(ObjUtil.isNotEmpty(reviewerId),"reviewerId",  reviewerId);
        pictureQueryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus),"reviewStatus", reviewStatus);
        pictureQueryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        if(CollUtil.isEmpty(tags)){
            for(String tag : tags){
                pictureQueryWrapper.like(StrUtil.isNotBlank(tag), "tags", "\"" + tag + "\"");
            }
        }
        pictureQueryWrapper.orderBy(StrUtil.isNotBlank(sortField), "ascend".equals(sortOrder), sortField);
        return pictureQueryWrapper;
    }

    @Override
    public PictureVO getPictureVO(Picture picture) {
        ThrowUtils.throwIf(picture == null , ErrorCode.PARAMS_ERROR);
        PictureVO pictureVO = PictureVO.objToVo(picture);
        Long id = picture.getId();
        if(id != null &&  id > 0){
            User user = userService.getById(id);
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        return pictureVO;
    }

    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage) {
        ThrowUtils.throwIf(picturePage == null , ErrorCode.PARAMS_ERROR);
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        if(CollUtil.isEmpty(pictureList)){
            return pictureVOPage;
        }
        //对象列表 => 封装对象列表
        List<PictureVO> pictureVOList = pictureList.stream().map(PictureVO::objToVo).collect(Collectors.toList());
        Set<Long> userIds = pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIds).stream()
                .collect(Collectors.groupingBy(User::getId));
        //填充信息
        for(PictureVO pictureVO : pictureVOList){
            Long userId = pictureVO.getUserId();
            if(userIdUserListMap.containsKey(userId)){
                pictureVO.setUser(userService.getUserVO(userIdUserListMap.get(userId).get(0)));
            }
        }
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }

    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null,  ErrorCode.PARAMS_ERROR);
        String url = picture.getUrl();
        Long id = picture.getId();
        String introduction = picture.getIntroduction();
        ThrowUtils.throwIf(ObjUtil.isEmpty(id),ErrorCode.PARAMS_ERROR,"id 不能为空");
        if(StrUtil.isNotBlank(url)){
            ThrowUtils.throwIf(url.length() > 1024,ErrorCode.PARAMS_ERROR,"url过长");
        }
        if(StrUtil.isNotBlank(introduction)){
            ThrowUtils.throwIf(introduction.length() > 800,ErrorCode.PARAMS_ERROR,"简介过长");
        }
    }

    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        //参数校验
        ThrowUtils.throwIf(pictureReviewRequest == null || loginUser == null, ErrorCode.PARAMS_ERROR);
        //取出内部字段
        Integer reviewStatus = PictureReviewStatusEnum.getEnumByValue(pictureReviewRequest.getReviewStatus()).getValue();
        String reviewMessage = pictureReviewRequest.getReviewMessage();
        Long reviewId = pictureReviewRequest.getId();
        ThrowUtils.throwIf(reviewId == null || reviewStatus == null ||
                PictureReviewStatusEnum.REVIEWING.getValue().equals(reviewStatus),  ErrorCode.PARAMS_ERROR);
        //判断图片是否存在
        Picture oldPicture = this.getById(reviewId);
        ThrowUtils.throwIf(oldPicture == null,  ErrorCode.NOT_FOUND_ERROR,"找不到该图片");
        //判断是否重复审核
        if(oldPicture.getReviewStatus().equals(reviewStatus)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"请勿重复审核");
        }
        //更新数据表picture
        Picture picture = new Picture();
        BeanUtil.copyProperties(pictureReviewRequest,picture);
        picture.setReviewTime(new Date());
        picture.setReviewerId(loginUser.getId());
        boolean update = this.updateById(picture);
        ThrowUtils.throwIf(!update,  ErrorCode.OPERATION_ERROR,"更新图片失败");
    }

    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        if(userService.isAdmin(loginUser)){
            //管理员自动过审
            picture.setReviewerId(loginUser.getId());
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewTime(new Date());
            picture.setReviewMessage("管理员自动过审");
        }else{
            //其他成员待审核
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
    }

    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        ThrowUtils.throwIf(pictureUploadByBatchRequest == null,ErrorCode.PARAMS_ERROR);
        String searchText = pictureUploadByBatchRequest.getSearchText();
        Integer count = pictureUploadByBatchRequest.getCount();
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
        if(StrUtil.isBlank(namePrefix)){
            namePrefix = searchText;
        }
        ThrowUtils.throwIf(count > 30,ErrorCode.PARAMS_ERROR,"最多30条");
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1",searchText);
        Document document;
        try{
            document = Jsoup.connect(fetchUrl).get();
        } catch (IOException e) {
            log.error("获取页面失败",e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"获取页面失败");
        }
        Element div = document.getElementsByClass("dgControl").first();
        if(ObjUtil.isNull(div)){
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"获取元素失败");
        }
        int uploadCount = 0;
        Elements imgElements = div.select(".iusc");
        for(Element imgElement : imgElements){
            String fileUrl = imgElement.attr("m");
            JSONObject jsonObject = new JSONObject(fileUrl);
            fileUrl = jsonObject.get("murl").toString();
            if(StrUtil.isBlank(fileUrl)){
                log.info("当前链接为空，已跳过....");
                continue;
            }
            //处理图片的url?后面的字符
            int index = fileUrl.indexOf("?");
            if(index > -1){
                fileUrl = fileUrl.substring(0,index);
            }
//            if(StrUtil.isBlank(FileUtil.extName(fileUrl))){
//                log.info("当前文件后缀名为空，已跳过....");
//                continue;
//            }
            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            if(StrUtil.isNotBlank(namePrefix)){
                pictureUploadRequest.setPicName(namePrefix + (uploadCount + 1));
            }
            try{
                PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                log.info("图片上传成功,图片id:{}",pictureVO.getId());
                uploadCount++;
            }catch (Exception e){
                log.error("图片上传失败,图片url:{}",fileUrl,e);
            }
            if(uploadCount >= count){
                break;
            }
        }


        return uploadCount;
    }
}




