package com.yupi.yupicturebackend.manager.websocket;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.yupi.yupicturebackend.manager.auth.SpaceAuthManager;
import com.yupi.yupicturebackend.manager.auth.model.SpaceUserPermissionConstant;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.SpaceTypeEnum;
import com.yupi.yupicturebackend.service.PictureService;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class WsHandshakeInterceptor implements HandshakeInterceptor {

    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private SpaceAuthManager spaceAuthManager;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        //获取请求参数
        HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
        String pictureId = servletRequest.getParameter("pictureId");
        if(StrUtil.isBlank(pictureId)){
            log.error("缺少图片参数，拒绝握手");
            return false;
        }
        User loginUser = userService.getLoginUser(servletRequest);
        if(ObjUtil.isNull(loginUser)){
            log.error("用户未登录，拒绝握手");
            return false;
        }
        //校验用户是否有该图片的权限
        Picture picture = pictureService.getById(pictureId);
        if(ObjUtil.isNull(picture)){
            log.error("图片不存在，拒绝握手");
            return false;
        }
        Long spaceId = picture.getSpaceId();
        Space space = null;
        if(spaceId != null){
            space = spaceService.getById(spaceId);
            if(ObjUtil.isNull(space)){
                log.error("空间不存在，拒绝握手");
                return false;
            }

            if(space.getSpaceType() != SpaceTypeEnum.TEAM.getValue()){
                log.error("非团队空间，拒绝握手");
                return false;
            }
        }

        List<String> permissionList = spaceAuthManager.getPermissionList(space, loginUser);
        if(!permissionList.contains(SpaceUserPermissionConstant.PICTURE_EDIT)){
            log.error("没有图片编辑权限，拒绝握手");
            return false;
        }
        //设置attribute
        attributes.put("user",loginUser);
        attributes.put("userId",loginUser.getId());
        attributes.put("pictureId",Long.valueOf(pictureId));
        
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {

    }
}
