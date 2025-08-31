package com.yupi.yupicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.yupicturebackend.model.dto.spaceuser.SpaceUserAddRequest;
import com.yupi.yupicturebackend.model.dto.spaceuser.SpaceUserQueryRequest;
import com.yupi.yupicturebackend.model.entity.SpaceUser;
import com.yupi.yupicturebackend.model.vo.SpaceUserVO;

import java.util.List;

/**
* @author admin
* @description 针对表【spaceuser_user(空间用户关联)】的数据库操作Service
* @createDate 2025-08-03 13:18:25
*/
public interface SpaceUserService extends IService<SpaceUser> {
    void validSpaceUser(SpaceUser spaceuser, boolean add);

    QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceuserQueryRequest);

    SpaceUserVO getSpaceUserVO(SpaceUser spaceuser);

    List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList);

    Long addSpaceUser(SpaceUserAddRequest spaceuserAddRequest);

}
