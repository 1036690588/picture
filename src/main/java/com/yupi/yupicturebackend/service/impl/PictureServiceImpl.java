package com.yupi.yupicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.yupicturebackend.api.aliyunai.AliyunAi;
import com.yupi.yupicturebackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.yupi.yupicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.manager.CosManager;
import com.yupi.yupicturebackend.manager.upload.FilePictureUpload;
import com.yupi.yupicturebackend.manager.upload.PictureUploadTemplate;
import com.yupi.yupicturebackend.manager.upload.UrlPictureUpload;
import com.yupi.yupicturebackend.mapper.PictureMapper;
import com.yupi.yupicturebackend.model.dto.file.PictureUploadRequest;
import com.yupi.yupicturebackend.model.dto.picture.*;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.PictureReviewStatusEnum;
import com.yupi.yupicturebackend.model.vo.PictureVO;
import com.yupi.yupicturebackend.model.vo.UserVO;
import com.yupi.yupicturebackend.service.PictureService;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.service.UserService;
import com.yupi.yupicturebackend.util.ColorSimilarUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.*;
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

    @Resource
    private CosManager cosManager;

    @Resource
    private SpaceService spaceService;
    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private AliyunAi aliyunAi;

    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        Long spaceId = pictureUploadRequest.getSpaceId();
        if(spaceId != null){
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR,"空间不存在");
            //只有空间管理人才能上传
//            if(!loginUser.getId().equals(space.getUserId())){
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"没有空间权限");
//            }
            if(space.getTotalCount() >= space.getMaxCount()){
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"空间内图片个数已满");
            }
            if(space.getTotalSize() >= space.getMaxSize()){
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"空间容量已满");
            }
        }
        //用于判断是否是新增还是更新图片
        ThrowUtils.throwIf(pictureUploadRequest == null, ErrorCode.PARAMS_ERROR);
        Long pictureId = pictureUploadRequest.getId();
        Picture oldPicture = null;
        if(pictureId != null){
            oldPicture = this.baseMapper.selectById(pictureId);
            //如果是更新图片，需要校验图片是否存在
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR,"图片不存在");
            if(spaceId == null){
                if(oldPicture.getSpaceId() != null){
                    spaceId = oldPicture.getSpaceId();
                }
            }else{
                if(!spaceId.equals(oldPicture.getSpaceId())){
                    throw new BusinessException(ErrorCode.PARAMS_ERROR,"空间id不一致");
                }
            }
            //如果图片不是当前用户上传的，或者不是管理员，则不能更新图片
//            if(!loginUser.getId().equals(oldPicture.getUserId())
//                    && !UserRoleEnum.ADMIN.getValue().equals(loginUser.getUserRole())){
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
//            }
        }
        //上传图片得到信息
        //按照用户id划分目录 String.format("public/%s",loginUser.getId());
        String uploadPathPrefix = spaceId == null ? String.format("public/%s",loginUser.getId())
                :  String.format("space/%s",spaceId);
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        if(inputSource instanceof String){
            pictureUploadTemplate = urlPictureUpload;
        }
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);



        //构造要入库的图片信息
        Picture picture = new Picture();
        picture.setUrl(uploadPictureResult.getUrl());
        picture.setThumbnailUrl(uploadPictureResult.getThumbnailUrl());
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
        picture.setSpaceId(spaceId);
        picture.setPicColor(uploadPictureResult.getPicColor());

        //补充审核参数
        this.fillReviewParams(picture, loginUser);
        //如果pictureId不为空，表示更新，否则是新增
        if(pictureId != null){
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }
        Long finalSpaceId = spaceId;
        transactionTemplate.execute(status -> {
            boolean result = this.saveOrUpdate(picture);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR,"图片上传失败");
            if(finalSpaceId != null){
                boolean update = spaceService.lambdaUpdate().eq(Space::getId, finalSpaceId)
                        .setSql("totalCount=totalCount+1")
                        .setSql("totalSize=totalSize+" + picture.getPicSize())
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR,"空间更新失败");
            }
            return true;
        });

        //删除更新前的旧图片
        if(oldPicture != null){
            this.clearPictureFile(oldPicture);
        }
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
        Long spaceId = pictureQueryRequest.getSpaceId();
        boolean nullSpaceId = pictureQueryRequest.isNullSpaceId();
        Date endEditTime = pictureQueryRequest.getEndEditTime();
        Date startEditTime = pictureQueryRequest.getStartEditTime();

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
        pictureQueryWrapper.eq(ObjUtil.isNotEmpty(spaceId),"spaceId", spaceId);
        pictureQueryWrapper.isNull(nullSpaceId, "spaceId");
        if(CollUtil.isNotEmpty(tags)){
            for(String tag : tags){
                pictureQueryWrapper.like(StrUtil.isNotBlank(tag), "tags", "\"" + tag + "\"");
            }
        }
        pictureQueryWrapper.orderBy(StrUtil.isNotBlank(sortField), "ascend".equals(sortOrder), sortField);
        //>=start <end
        pictureQueryWrapper.ge(ObjUtil.isNotEmpty(startEditTime), "editTime", startEditTime);
        pictureQueryWrapper.lt(ObjUtil.isNotEmpty(endEditTime), "editTime", endEditTime);
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

    @Async
    @Override
    public void clearPictureFile(Picture picture) {
        //判断该图片是否被多条记录使用
        String url = picture.getUrl();
        Long count = this.lambdaQuery().eq(Picture::getUrl, url).count();
        //有不止一条记录使用该图片，则不清理
        if(count > 1){
            return;
        }
        //清理
        cosManager.deleteObject(url);
        //清理缩略图
        String thumbnailUrl = picture.getThumbnailUrl();
        if(StrUtil.isNotBlank(thumbnailUrl)){
            cosManager.deleteObject(thumbnailUrl);
        }
    }

    @Override
    public void checkPictureAuth(Picture picture, User loginUser) {
        Long spaceId = picture.getSpaceId();
        if(spaceId != null){
            //私有空间，仅空间管理员操作
            if(!loginUser.getId().equals(picture.getUserId())){
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }else{
            //公告图库空间，仅管理员和本人操作
            if(!loginUser.getId().equals(picture.getUserId()) && !userService.isAdmin(loginUser)){
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }
    }

    @Override
    public boolean deletePicture(Long pictureId, User loginUser) {
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.PARAMS_ERROR);
        Picture oldPicture = this.getById(pictureId);
        if(oldPicture == null){
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        //仅本人或管理员才可删除
        this.checkPictureAuth(oldPicture, loginUser);
        transactionTemplate.execute(status -> {
            boolean result = this.removeById(pictureId);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            Long spaceId = oldPicture.getSpaceId();
            if(spaceId != null){
                boolean update = spaceService.lambdaUpdate().eq(Space::getId, oldPicture.getSpaceId())
                        .setSql("totalSize = totalSize - " + oldPicture.getPicSize())
                        .setSql("totalCount = totalCount - 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR,"更新额度失败");
            }
            return true;
        });

        //删除COS内部图片
        this.clearPictureFile(oldPicture);
        return true;
    }

    @Override
    public void editPicture(PictureEditRequest pictureEditRequest, User loginUser) {
        //将实体类和DTO转换
        Picture picture = new Picture();
        BeanUtil.copyProperties(pictureEditRequest, picture);
        //将list转成json字符串
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        //设置编辑时间
        picture.setEditTime(new Date());
        //图片校验
        this.validPicture(picture);
        //判断是否存在
        Picture oldPicture = this.getById(pictureEditRequest.getId());
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        //仅本人或管理员可编辑
        this.checkPictureAuth(oldPicture, loginUser);
        this.fillReviewParams(picture, loginUser);
        //操作数据库
        boolean update = this.updateById(picture);
        ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR);
    }

    @Override
    public List<PictureVO> searchPictureByColor(Long spaceId, String color, User loginUser) {
        //校验参数
        ThrowUtils.throwIf(spaceId == null || StrUtil.isBlank(color), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.PARAMS_ERROR);
        //校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR,"空间不存在");
        ThrowUtils.throwIf(!loginUser.getId().equals(space.getUserId()),
                ErrorCode.NO_AUTH_ERROR,"没有空间访问权限");
        //查询空间下所有的图片（必须有主色调）
        List<Picture> pictureList = this.lambdaQuery().eq(Picture::getSpaceId, spaceId)
                .isNotNull(Picture::getPicColor)
                .list();
        //如果没有图片，返回空列表
        if(CollUtil.isEmpty(pictureList)){
            return Collections.emptyList();
        }
        //将目标颜色转为Color对象
        Color targetColor = Color.decode(color);
        //计算相似度并排序
        List<Picture> sortedPicture = pictureList.stream().sorted(Comparator.comparingDouble(picture -> {
            String picColor = picture.getPicColor();
            //没有主色调放到最后
            if(StrUtil.isBlank(picColor)){
                return Double.MAX_VALUE;
            }
            Color decodeColor = Color.decode(picColor);
            return ColorSimilarUtils.calculateSimilarity(decodeColor, targetColor);
        })).limit(12).collect(Collectors.toList());
        //转化为PictureVO
        return sortedPicture.stream().map(PictureVO::objToVo).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        List<Long> pictureIdList = pictureEditByBatchRequest.getPictureIdList();
        String category = pictureEditByBatchRequest.getCategory();
        List<String> tags = pictureEditByBatchRequest.getTags();
        Long spaceId = pictureEditByBatchRequest.getSpaceId();
        String namingRule = pictureEditByBatchRequest.getNamingRule();

        //权限校验
        ThrowUtils.throwIf(spaceId == null,ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null,ErrorCode.PARAMS_ERROR);
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR,"空间不存在");
        if(space.getUserId().equals(loginUser.getId())){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        List<Picture> pictureList = this.lambdaQuery().select(Picture::getSpaceId, Picture::getId)
                .eq(Picture::getSpaceId, spaceId)
                .in(Picture::getId, pictureIdList)
                .list();
        if(pictureList.isEmpty()){
            return;
        }
        fillPictureWithNameRule(pictureList, namingRule);
        for (Picture picture : pictureList){
            if(StrUtil.isNotBlank(category)){
                picture.setCategory(category);
            }
            if(CollUtil.isNotEmpty(tags)){
                String jsonTag = JSONUtil.toJsonStr(tags);
                picture.setTags(jsonTag);
            }
        }

        boolean update = this.updateBatchById(pictureList);
        ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR);

    }

    @Override
    public CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest,
                                                                      User loginUser) {
        Long pictureId = createPictureOutPaintingTaskRequest.getPictureId();
        ThrowUtils.throwIf(pictureId == null, ErrorCode.PARAMS_ERROR);
        Picture picture = this.getById(pictureId);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR,"未找到图片");
        checkPictureAuth(picture, loginUser);
        CreateOutPaintingTaskRequest taskRequest = new CreateOutPaintingTaskRequest();
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
        input.setImageUrl(picture.getUrl());
        taskRequest.setInput(input);
        BeanUtil.copyProperties(createPictureOutPaintingTaskRequest, taskRequest);
        //创建任务
        return aliyunAi.createOutPaintingTask(taskRequest);
    }

    private void fillPictureWithNameRule(List<Picture> pictureList, String namingRule) {
        if(StrUtil.isBlank(namingRule) || CollUtil.isEmpty(pictureList)){
            return;
        }
        int count = 1;
        try{
            for (Picture picture : pictureList) {
                String name = namingRule.replaceAll("\\{序号}", String.valueOf(count++));
                picture.setName(name);
            }
        }catch (Exception e){
            log.error("名称解析错误",e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"名称解析错误");
        }


    }
}




