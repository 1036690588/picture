package com.yupi.yupicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.mapper.SpaceUserMapper;
import com.yupi.yupicturebackend.model.dto.spaceuser.SpaceUserAddRequest;
import com.yupi.yupicturebackend.model.dto.spaceuser.SpaceUserQueryRequest;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.SpaceUser;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.SpaceRoleEnum;
import com.yupi.yupicturebackend.model.vo.SpaceUserVO;
import com.yupi.yupicturebackend.model.vo.space.SpaceVO;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.service.SpaceUserService;
import com.yupi.yupicturebackend.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
* @author admin
* @description 针对表【space_user(空间用户关联)】的数据库操作Service实现
* @createDate 2025-08-03 13:18:25
*/
@Service
public class SpaceUserServiceImpl extends ServiceImpl<SpaceUserMapper, SpaceUser>
    implements SpaceUserService{

    @Resource
    private UserService userService;

    @Lazy
    @Resource
    private SpaceService spaceService;

    @Override
    public void validSpaceUser(SpaceUser spaceuser, boolean add) {
        ThrowUtils.throwIf(spaceuser == null, ErrorCode.PARAMS_ERROR);
        Long userId = spaceuser.getUserId();
        Long spaceId = spaceuser.getSpaceId();
        if(add){
            //查询此用户是否存在
            boolean userExists = userService.lambdaQuery().eq(User::getId, userId)
                    .exists();
            ThrowUtils.throwIf(!userExists, ErrorCode.NOT_FOUND_ERROR,"用户不存在");
            //查询此空间是否存在
            boolean spaceExists = spaceService.lambdaQuery().eq(Space::getId, spaceId)
                    .exists();
            ThrowUtils.throwIf(!spaceExists, ErrorCode.NOT_FOUND_ERROR,"空间不存在");
        }
        String spaceRole = spaceuser.getSpaceRole();
        ThrowUtils.throwIf(spaceRole == null, ErrorCode.PARAMS_ERROR,"空间角色不能为空");
        SpaceRoleEnum roleEnum = SpaceRoleEnum.getEnumByValue(spaceRole);
        ThrowUtils.throwIf(roleEnum == null,  ErrorCode.PARAMS_ERROR, "空间角色不存在");
    }

    @Override
    public QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceuserQueryRequest) {
        ThrowUtils.throwIf(spaceuserQueryRequest == null, ErrorCode.PARAMS_ERROR);
        //从对象中取值
        String spaceRole = spaceuserQueryRequest.getSpaceRole();
        Long id = spaceuserQueryRequest.getId();
        Long userId = spaceuserQueryRequest.getUserId();
        Long spaceId = spaceuserQueryRequest.getSpaceId();

        QueryWrapper<SpaceUser> spaceUserQueryWrapper = new QueryWrapper<>();
        spaceUserQueryWrapper.eq(ObjUtil.isNotEmpty(id) , "id", id);
        spaceUserQueryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        spaceUserQueryWrapper.eq(ObjUtil.isNotEmpty(spaceRole), "spaceRole", spaceRole);
        spaceUserQueryWrapper.eq(ObjUtil.isNotEmpty(spaceId) , "spaceId", spaceId);

        return spaceUserQueryWrapper;
    }

    @Override
    public SpaceUserVO getSpaceUserVO(SpaceUser spaceuser) {
        SpaceUserVO spaceUserVO = SpaceUserVO.objToVo(spaceuser);
        Long userId = spaceuser.getUserId();
        if(ObjUtil.isNotEmpty(userId) && userId > 0){
            User user = userService.getById(userId);
            spaceUserVO.setUser(userService.getUserVO(user));
        }
        Long spaceId = spaceuser.getSpaceId();
        if(ObjUtil.isNotEmpty(spaceId) && spaceId > 0){
            Space space = spaceService.getById(spaceId);
            spaceUserVO.setSpace(spaceService.getSpaceVO(space));
        }
        return spaceUserVO;
    }

    @Override
    public List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList) {
        // 判断输入列表是否为空
        if (CollUtil.isEmpty(spaceUserList)) {
            return Collections.emptyList();
        }
        // 对象列表 => 封装对象列表
        List<SpaceUserVO> spaceUserVOList = spaceUserList.stream().map(SpaceUserVO::objToVo).collect(Collectors.toList());
        // 1. 收集需要关联查询的用户 ID 和空间 ID
        Set<Long> userIdSet = spaceUserList.stream().map(SpaceUser::getUserId).collect(Collectors.toSet());
        Set<Long> spaceIdSet = spaceUserList.stream().map(SpaceUser::getSpaceId).collect(Collectors.toSet());
        // 2. 批量查询用户和空间
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        Map<Long, List<Space>> spaceIdSpaceListMap = spaceService.listByIds(spaceIdSet).stream()
                .collect(Collectors.groupingBy(Space::getId));
        // 3. 填充 SpaceUserVO 的用户和空间信息
        spaceUserVOList.forEach(spaceUserVO -> {
            Long userId = spaceUserVO.getUserId();
            Long spaceId = spaceUserVO.getSpaceId();
            // 填充用户信息
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            spaceUserVO.setUser(userService.getUserVO(user));
            // 填充空间信息
            Space space = null;
            if (spaceIdSpaceListMap.containsKey(spaceId)) {
                space = spaceIdSpaceListMap.get(spaceId).get(0);
            }
            spaceUserVO.setSpace(SpaceVO.objToVo(space));
        });
        return spaceUserVOList;
    }



    @Override
    public Long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest) {
        // 参数校验
        ThrowUtils.throwIf(spaceUserAddRequest == null, ErrorCode.PARAMS_ERROR);
        SpaceUser spaceUser = new SpaceUser();
        BeanUtils.copyProperties(spaceUserAddRequest, spaceUser);
        validSpaceUser(spaceUser, true);
        // 数据库操作
        boolean result = this.save(spaceUser);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return spaceUser.getId();
    }

}




