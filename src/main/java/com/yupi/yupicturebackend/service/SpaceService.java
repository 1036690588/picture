package com.yupi.yupicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.yupicturebackend.model.dto.space.SpaceAddRequest;
import com.yupi.yupicturebackend.model.dto.space.SpaceQueryRequest;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.space.SpaceVO;

/**
* @author admin
* @description 针对表【space(空间)】的数据库操作Service
* @createDate 2025-06-11 20:11:32
*/
public interface SpaceService extends IService<Space> {
    void validSpace(Space space,boolean add);

    void fillSpaceBySpaceLevel(Space space);

    QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);

    SpaceVO getSpaceVO(Space space);

    Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage);

    Long addSpace(SpaceAddRequest spaceAddRequest, User loginUser);

    void checkSpaceAuth(Space space, User loginUser);
}
