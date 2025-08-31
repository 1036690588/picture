package com.yupi.yupicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.mapper.SpaceMapper;
import com.yupi.yupicturebackend.model.dto.space.SpaceAddRequest;
import com.yupi.yupicturebackend.model.dto.space.SpaceQueryRequest;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.SpaceUser;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.SpaceLevelEnum;
import com.yupi.yupicturebackend.model.enums.SpaceRoleEnum;
import com.yupi.yupicturebackend.model.enums.SpaceTypeEnum;
import com.yupi.yupicturebackend.model.enums.UserRoleEnum;
import com.yupi.yupicturebackend.model.vo.UserVO;
import com.yupi.yupicturebackend.model.vo.space.SpaceVO;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.service.SpaceUserService;
import com.yupi.yupicturebackend.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
* @author admin
* @description 针对表【space(空间)】的数据库操作Service实现
* @createDate 2025-06-11 20:11:32
*/
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space>
    implements SpaceService{

    @Resource
    private UserService userService;

    @Resource
    private TransactionTemplate transactionTemplate;

    Map<Long,Object> lockMap = new ConcurrentHashMap<>();
    @Resource
    private SpaceUserService spaceUserService;

//    @Resource
//    @Lazy
//    private DynamicShardingManager dynamicSharingManager;


    @Override
    public void validSpace(Space space, boolean add) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR);
        String spaceName = space.getSpaceName();
        Integer spaceLevel = space.getSpaceLevel();
        Integer spaceType = space.getSpaceType();
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);
        SpaceTypeEnum spaceTypeEnum = SpaceTypeEnum.getEnumByValue(spaceType);
        //若要创建空间
        if(add){
            if(StrUtil.isBlank(spaceName)){
                throw new BusinessException(ErrorCode.PARAMS_ERROR,"空间名称不能为空");
            }
            if(ObjUtil.isNull(spaceLevel)){
                throw new BusinessException(ErrorCode.PARAMS_ERROR,"空间等级不能为空");
            }
            if(ObjUtil.isNull(spaceType)){
                throw new BusinessException(ErrorCode.PARAMS_ERROR,"空间类型不能为空");
            }
        }
        if(spaceLevel != null && spaceLevelEnum == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"空间等级不存在");
        }
        if(spaceType == null && spaceTypeEnum == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"空间类型不存在");
        }
        if(StrUtil.isNotBlank(spaceName) && spaceName.length() > 30){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"空间名称过长");
        }
    }

    @Override
    public void fillSpaceBySpaceLevel(Space space) {
        //根据空间级别，自动填充限额
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());
        ThrowUtils.throwIf(spaceLevelEnum == null,  ErrorCode.PARAMS_ERROR);
        if(space.getMaxCount() == null){
            space.setMaxCount(spaceLevelEnum.getMaxCount());
        }
        if(space.getMaxSize() == null){
            space.setMaxSize(spaceLevelEnum.getMaxSize());
        }
    }

    @Override
    public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest) {
        ThrowUtils.throwIf(spaceQueryRequest == null, ErrorCode.PARAMS_ERROR);
        //从对象中取值
        Integer spaceLevel = spaceQueryRequest.getSpaceLevel();
        Integer spaceType = spaceQueryRequest.getSpaceType();
        Long id = spaceQueryRequest.getId();
        String spaceName = spaceQueryRequest.getSpaceName();
        Long userId = spaceQueryRequest.getUserId();
        String sortField = spaceQueryRequest.getSortField();
        String sortOrder = spaceQueryRequest.getSortOrder();

        QueryWrapper<Space> spaceQueryWrapper = new QueryWrapper<>();
        spaceQueryWrapper.eq(ObjUtil.isNotEmpty(id) , "id", id);
        spaceQueryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        spaceQueryWrapper.eq(ObjUtil.isNotEmpty(spaceLevel), "spaceLevel", spaceLevel);
        spaceQueryWrapper.eq(ObjUtil.isNotEmpty(spaceType) , "spaceType", spaceType);
        spaceQueryWrapper.like(StrUtil.isNotBlank(spaceName), "spaceName", spaceName);

        spaceQueryWrapper.orderBy(StrUtil.isNotBlank(sortField), "ascend".equals(sortOrder), sortField);
        return spaceQueryWrapper;
    }

    @Override
    public SpaceVO getSpaceVO(Space space) {
        ThrowUtils.throwIf(space == null , ErrorCode.PARAMS_ERROR);
        SpaceVO spaceVO = SpaceVO.objToVo(space);
        Long id = space.getId();
        if(id != null &&  id > 0){
            User user = userService.getById(id);
            UserVO userVO = userService.getUserVO(user);
            spaceVO.setUser(userVO);
        }
        return spaceVO;
    }

    @Override
    public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage) {
        ThrowUtils.throwIf(spacePage == null , ErrorCode.PARAMS_ERROR);
        List<Space> spaceList = spacePage.getRecords();
        Page<SpaceVO> spaceVOPage = new Page<>(spacePage.getCurrent(), spacePage.getSize(), spacePage.getTotal());
        if(CollUtil.isEmpty(spaceList)){
            return spaceVOPage;
        }
        //对象列表 => 封装对象列表
        List<SpaceVO> spaceVOList = spaceList.stream().map(SpaceVO::objToVo).collect(Collectors.toList());
        Set<Long> userIds = spaceList.stream().map(Space::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIds).stream()
                .collect(Collectors.groupingBy(User::getId));
        //填充信息
        for(SpaceVO spaceVO : spaceVOList){
            Long userId = spaceVO.getUserId();
            if(userIdUserListMap.containsKey(userId)){
                spaceVO.setUser(userService.getUserVO(userIdUserListMap.get(userId).get(0)));
            }
        }
        spaceVOPage.setRecords(spaceVOList);
        return spaceVOPage;
    }

    @Override
    public Long addSpace(SpaceAddRequest spaceAddRequest, User loginUser) {
        //填充参数默认值
        Space space = new Space();
        BeanUtil.copyProperties(spaceAddRequest,space);
        if(StrUtil.isBlank(space.getSpaceName())){
            space.setSpaceName("默认空间");
        }
        if(ObjUtil.isNull(space.getSpaceLevel())){
            space.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }
        if(ObjUtil.isNull(space.getSpaceType())){
            space.setSpaceType(SpaceTypeEnum.PRIVATE.getValue());
        }
        this.fillSpaceBySpaceLevel(space);
        //校验参数
        this.validSpace(space,true);
        Long userId = loginUser.getId();
        space.setUserId(userId);
        //校验权限，非管理员只能创建普通级别的空间
        if(!UserRoleEnum.ADMIN.getValue().equals(loginUser.getUserRole())
                && !SpaceLevelEnum.COMMON.getValue().equals(space.getSpaceLevel())){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        //控制同一用户只能创建一个私有空间
        Object lock = lockMap.computeIfAbsent(userId, k -> new Object());
        synchronized (lock){
            try{
                Long newSpaceId = transactionTemplate.execute(status -> {
                    //数据库中添加空间操作
                    if(!userService.isAdmin(loginUser)){
                        boolean exists = this.lambdaQuery()
                                .eq(Space::getUserId, userId)
                                .eq(Space::getSpaceType, spaceAddRequest.getSpaceType())
                                .exists();
                        ThrowUtils.throwIf(exists, ErrorCode.OPERATION_ERROR,"每个用户每类空间仅能创建一个");
                    }
                    boolean save = this.save(space);
                    ThrowUtils.throwIf(!save, ErrorCode.OPERATION_ERROR);
                    //如果是团队空间，关联新增团队成员记录
                    if(SpaceTypeEnum.TEAM.getValue() == spaceAddRequest.getSpaceType()){
                        SpaceUser spaceUser = new SpaceUser();
                        spaceUser.setSpaceId(space.getId());
                        spaceUser.setUserId(userId);
                        spaceUser.setSpaceRole(SpaceRoleEnum.ADMIN.getValue());
                        save = spaceUserService.save(spaceUser);
                        ThrowUtils.throwIf(!save, ErrorCode.OPERATION_ERROR);
                    }
                    //创建分表
//                    dynamicSharingManager.createSpacePictureTable(space);
                    return space.getId();
                });
                return Optional.ofNullable(newSpaceId).orElse(-1L);
            }finally {
                //记得释放，防止内存泄漏
                lockMap.remove(userId);
            }

        }

    }

    @Override
    public void checkSpaceAuth(Space space, User loginUser) {
        Long userId = space.getUserId();
        if(!userId.equals(loginUser.getId()) && !userService.isAdmin(loginUser)){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"无查阅此空间的权限");
        }
    }


}




