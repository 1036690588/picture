package com.yupi.yupicturebackend.service.impl;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.model.dto.space.analyze.SpaceAnalyzeRequest;
import com.yupi.yupicturebackend.model.dto.space.analyze.SpaceRankAnalyzeRequest;
import com.yupi.yupicturebackend.model.dto.space.analyze.SpaceUsageAnalyzeRequest;
import com.yupi.yupicturebackend.model.dto.space.analyze.SpaceUserAnalyzeRequest;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.space.analyze.*;
import com.yupi.yupicturebackend.service.PictureService;
import com.yupi.yupicturebackend.service.SpaceAnalyzeService;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.service.UserService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
@Service
public class SpaceAnalyzeServiceImpl implements SpaceAnalyzeService {

    @Resource
    private UserService userService;
    @Resource
    private SpaceService spaceService;
    @Resource
    private PictureService pictureService;

    @Override
    public SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceUsageAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        if(spaceUsageAnalyzeRequest.isQueryAll() || spaceUsageAnalyzeRequest.isQueryPublic()){
            //查询全部或公共图库逻辑
            //仅管理员可以访问
            ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR,"无权访问公共图库或全部图库");
            QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
            fillAnalyzeQueryWrapper(spaceUsageAnalyzeRequest, queryWrapper);
            queryWrapper.select("picSize");
            List<Object> pictureObjList = pictureService.getBaseMapper().selectObjs(queryWrapper);
            long usedSize = pictureObjList.stream().mapToLong(obj -> (Long) obj).sum();
            long usedCount = pictureObjList.size();
            SpaceUsageAnalyzeResponse spaceUsageAnalyzeResponse = new SpaceUsageAnalyzeResponse();
            spaceUsageAnalyzeResponse.setUsedSize(usedSize);
            spaceUsageAnalyzeResponse.setUsedCount(usedCount);
            spaceUsageAnalyzeResponse.setCountUsageRatio(null);
            spaceUsageAnalyzeResponse.setSizeUsageRatio(null);
            spaceUsageAnalyzeResponse.setMaxSize(null);
            spaceUsageAnalyzeResponse.setMaxCount(null);
            return spaceUsageAnalyzeResponse;
        }else{
            Long spaceId = spaceUsageAnalyzeRequest.getSpaceId();
            ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR);
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR,"空间不存在");
            spaceService.checkSpaceAuth(space, loginUser);
            SpaceUsageAnalyzeResponse spaceUsageAnalyzeResponse = new SpaceUsageAnalyzeResponse();
            spaceUsageAnalyzeResponse.setMaxSize(space.getMaxSize());
            spaceUsageAnalyzeResponse.setMaxCount(space.getMaxCount());
            spaceUsageAnalyzeResponse.setUsedSize(space.getTotalSize());
            spaceUsageAnalyzeResponse.setUsedCount(space.getTotalCount());
            double sizeUsageRatio = NumberUtil.round(space.getTotalSize() * 100.0 / space.getMaxSize(), 2).doubleValue();
            spaceUsageAnalyzeResponse.setSizeUsageRatio(sizeUsageRatio);
            double CountUsageRatio = NumberUtil.round(space.getTotalCount() * 100.0 / space.getMaxCount(), 2).doubleValue();
            spaceUsageAnalyzeResponse.setCountUsageRatio(CountUsageRatio);
            return spaceUsageAnalyzeResponse;

        }

    }

    @Override
    public List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(SpaceAnalyzeRequest spaceAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        checkSpaceAnalyzeAuth(spaceAnalyzeRequest, loginUser);
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceAnalyzeRequest, queryWrapper);

        queryWrapper.select(
                "category as category",
                "COUNT(*) as count",
                "SUM(picSize) as totalSize"
        ).groupBy("category");
        List<SpaceCategoryAnalyzeResponse> categoryAnalyzeResponseList = pictureService.getBaseMapper().selectMaps(queryWrapper).stream()
                .map(map -> {
                    SpaceCategoryAnalyzeResponse categoryAnalyzeResponse = new SpaceCategoryAnalyzeResponse();
                    categoryAnalyzeResponse.setCategory(map.get("category") != null ? map.get("category").toString() : "未分类");
                    categoryAnalyzeResponse.setCount(Long.parseLong(map.get("count").toString()));
                    categoryAnalyzeResponse.setTotalSize(Long.parseLong(map.get("totalSize").toString()));
                    return categoryAnalyzeResponse;
                }).collect(Collectors.toList());
        return categoryAnalyzeResponseList;
    }

    @Override
    public List<SpaceTagAnalyzeResponse> getSpaceTagAnalyze(SpaceAnalyzeRequest spaceAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        checkSpaceAnalyzeAuth(spaceAnalyzeRequest, loginUser);
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceAnalyzeRequest, queryWrapper);
        queryWrapper.select("tags");
        //获取所有非空标签列表,此时每个标签列表都是Json格式["A","B",...]
        List<String> tagsJsonList = pictureService.getBaseMapper().selectObjs(queryWrapper)
                .stream()
                .filter(ObjUtil::isNotNull)
                .map(Object::toString)
                .collect(Collectors.toList());
        //扁平化Json格式内标签列表，再转换成Map，key是单个标签，value是标签出现的次数
        Map<String, Long> map = tagsJsonList.stream()
                .flatMap(tagJson -> JSONUtil.toList(tagJson, String.class).stream())
                .collect(Collectors.groupingBy(tag -> tag, Collectors.counting()));
        //生成SpaceTagAnalyzeResponse响应列表
        return map.entrySet().stream()
                .map(entry -> new SpaceTagAnalyzeResponse(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * 获取空间大小分析
     * @param spaceAnalyzeRequest
     * @param loginUser
     * @return
     */
    @Override
    public List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyze(SpaceAnalyzeRequest spaceAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        //校验权限
        checkSpaceAnalyzeAuth(spaceAnalyzeRequest, loginUser);
        //填充QueryWrapper条件
        QueryWrapper<Picture> pictureQueryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceAnalyzeRequest, pictureQueryWrapper);
        pictureQueryWrapper.select("picSize");
        List<Long> sizeList = pictureService.getBaseMapper().selectObjs(pictureQueryWrapper)
                .stream()
                .map(obj -> ((Number) obj).longValue())
                .collect(Collectors.toList());

        LinkedHashMap<String, Long> linkedHashMap = new LinkedHashMap<>();
        linkedHashMap.put("<100KB",sizeList.stream().filter(size -> size < 100 * 1024).count());
        linkedHashMap.put("100KB-500KB",sizeList.stream().filter(size -> size >= 100 * 1024 && size < 500 * 1024).count());
        linkedHashMap.put("500KB-1MB",sizeList.stream().filter(size -> size >= 500 * 1024 && size < 1024 * 1024).count());
        linkedHashMap.put(">1MB",sizeList.stream().filter(size -> size >= 1024 * 1024).count());

        return linkedHashMap.entrySet().stream()
                .map(entry -> new SpaceSizeAnalyzeResponse(entry.getKey(),entry.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * 获取空间用户分析
     * @param spaceUserAnalyzeRequest
     * @param loginUser
     * @return
     */
    @Override
    public List<SpaceUserAnalyzeResponse> getSpaceUserAnalyze(SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceUserAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        checkSpaceAnalyzeAuth(spaceUserAnalyzeRequest, loginUser);
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        Long userId = spaceUserAnalyzeRequest.getUserId();
        queryWrapper.eq( ObjUtil.isNotNull(userId),
                "userId", spaceUserAnalyzeRequest.getUserId());
        fillAnalyzeQueryWrapper(spaceUserAnalyzeRequest, queryWrapper);

        String timeDimension = spaceUserAnalyzeRequest.getTimeDimension();
        switch (timeDimension){
            case "day":
                queryWrapper.select(
                        "DATE_FORMAT(createTime,'%Y-%m-%d') as period",
                        "COUNT(*) as count"
                );
                break;
            case "week":
                queryWrapper.select(
                        "YEARWEEK(createTime) as period",
                        "COUNT(*) as count"
                );
                break;
            case "month":
                queryWrapper.select(
                        "DATE_FORMAT(createTime,'%Y-%m') as period",
                        "COUNT(*) as count"
                );
                break;
            default:
                throw new BusinessException(ErrorCode.PARAMS_ERROR,"不支持的时间维度");
        }
        queryWrapper.groupBy("period").orderByAsc("period");
        List<Map<String, Object>> mapList = pictureService.getBaseMapper().selectMaps(queryWrapper);

        return mapList.stream()
                .map(map -> {
                  String period = map.get("period").toString();
                  Long count = ((Number) map.get("count")).longValue();
                  return new SpaceUserAnalyzeResponse(period,count);
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取空间排行分析
     * @param spaceRankAnalyzeRequest
     * @param loginUser
     * @return
     */
    @Override
    public List<Space> getSpaceRankAnalyze(SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceRankAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(!userService.isAdmin(loginUser),ErrorCode.NO_AUTH_ERROR,"无权查看空间排行");
        QueryWrapper<Space> spaceQueryWrapper = new QueryWrapper<>();
        spaceQueryWrapper.select("id", "spaceName", "totalCount", "totalSize")
                .orderByAsc("totalSize")
                .last("LIMIT " + spaceRankAnalyzeRequest.getTopN()); //取前N个空间
        return spaceService.list(spaceQueryWrapper);
    }


    /**
     * 根据分析范围填充查询对象
     * @param spaceAnalyzeRequest
     * @param queryWrapper
     */
    private void fillAnalyzeQueryWrapper(SpaceAnalyzeRequest spaceAnalyzeRequest,
                                         QueryWrapper<Picture> queryWrapper){
        if(spaceAnalyzeRequest.isQueryAll()){
            return;
        }else if (spaceAnalyzeRequest.isQueryPublic()){
            queryWrapper.isNull("spaceId");
            return;
        }
        Long spaceId = spaceAnalyzeRequest.getSpaceId();
        if(spaceId != null){
            queryWrapper.eq("spaceId", spaceId);
            return;
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR,"未指定查询范围");
    }

    /**
     * 校验空间分析权限
     * @param spaceAnalyzeRequest
     * @param loginUser
     */
    private void checkSpaceAnalyzeAuth(SpaceAnalyzeRequest spaceAnalyzeRequest,
                                       User loginUser){
        if(spaceAnalyzeRequest.isQueryAll() || spaceAnalyzeRequest.isQueryPublic()){
            //若查询公共图库或全部图库，则需要管理员权限
            ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR,"无权访问公共图库或全部图库");
        }else{
            //查询私有图库
            Long spaceId = spaceAnalyzeRequest.getSpaceId();
            ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR);
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR,"空间不存在");
            spaceService.checkSpaceAuth(space, loginUser);
        }
    }




}
