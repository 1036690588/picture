package com.yupi.yupicturebackend.manager.auth;

import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.ServletUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import cn.hutool.json.JSONUtil;
import com.yupi.yupicturebackend.constant.UserConstant;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.manager.auth.model.SpaceUserPermissionConstant;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.SpaceUser;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.SpaceRoleEnum;
import com.yupi.yupicturebackend.model.enums.SpaceTypeEnum;
import com.yupi.yupicturebackend.service.PictureService;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.service.SpaceUserService;
import com.yupi.yupicturebackend.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;


@Component
public class StpInterfaceImpl implements StpInterface {

    @Value("${server.servlet.context-path}")
    private String contextPath;
    @Resource
    private SpaceAuthManager spaceAuthManager;
    @Resource
    private SpaceUserService spaceUserService;
    @Resource
    private PictureService pictureService;
    @Resource
    private UserService userService;
    @Resource
    private SpaceService spaceService;


    @Override
    public List<String> getPermissionList(Object objectId, String loginType) {
        //判断loginType,仅对类型为space校验
        if(!StpKit.SPACE_TYPE.equals(loginType)){
            return new ArrayList<>();
        }
        //管理员权限
        List<String> ADMIN_PERMISSIONS = spaceAuthManager.getPermissionByRole(SpaceRoleEnum.ADMIN.getValue());
        //获取请求上下文对象
        SpaceUserAuthContext authContext = getSpaceUserAuthContext();
        //如果authContext都为空，则视为查询公共图库，通过
        if(isAllFieldNull(authContext)){
            return ADMIN_PERMISSIONS;
        }
        User user = (User)StpKit.SPACE.getSessionByLoginId(objectId).get(UserConstant.USER_LOGIN_STATE);
        if(ObjUtil.isNull( user)){
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR,"用户未登录");
        }
        //获取userId,以便后续判断
        Long userId = user.getId();

        SpaceUser spaceUser = authContext.getSpaceUser();
        if(ObjUtil.isNotEmpty(spaceUser)){
            return spaceAuthManager.getPermissionByRole(spaceUser.getSpaceRole());
        }
        //获取spaceUserId
        Long spaceUserId = authContext.getSpaceUserId();
        if(ObjUtil.isNotEmpty(spaceUserId)){
            spaceUser = spaceUserService.getById(spaceUserId);
            ThrowUtils.throwIf(spaceUser == null,ErrorCode.NOT_FOUND_ERROR,"未找到spaceUser对象");
            //获取当前用户对应的SpaceUser
            SpaceUser loginSpaceUser = spaceUserService.lambdaQuery()
                    .eq(SpaceUser::getUserId, userId)
                    .eq(SpaceUser::getSpaceId, spaceUser.getSpaceId())
                    .one();
            if (loginSpaceUser == null){
                return new ArrayList<>();
            }
            return spaceAuthManager.getPermissionByRole(loginSpaceUser.getSpaceRole());
        }

        //如果没有spaceUserId,则尝试通过spaceId或PictureId获取Space对象并处理
        Long spaceId = authContext.getSpaceId();
        if(spaceId == null){
            //如果没有SpaceId，PictureId获取和picture对象和Space对象并处理
            Long pictureId = authContext.getPictureId();
            //spaceId、pictureId都为null,视为管理员权限
            if(pictureId == null){
                return ADMIN_PERMISSIONS;
            }
            Picture picture = pictureService.lambdaQuery()
                    .eq(Picture::getId, pictureId)
                    .select(Picture::getId, Picture::getSpaceId, Picture::getUserId)
                    .one();
            ThrowUtils.throwIf(picture == null,ErrorCode.NOT_FOUND_ERROR,"未找到picture对象");
            spaceId = picture.getSpaceId();
            //图片对象中的spaceId是null，则当前图片是在公共图库中
            if(spaceId == null){
                //若当前用户是管理员或是图片上传人，则返回所有权限
                if(userService.isAdmin(user) || userId.equals(picture.getUserId())){
                    return ADMIN_PERMISSIONS;
                }else{
                    //不是自己的图片，只能查看
                    return Collections.singletonList(SpaceUserPermissionConstant.PICTURE_VIEW);
                }
            }
        }
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null,ErrorCode.NOT_FOUND_ERROR,"未找到space对象");
        if(SpaceTypeEnum.PRIVATE.getValue() == space.getSpaceType()){
            //私人空间
            //若是管理员或本人，则有权限
            if(userService.isAdmin(user) || userId.equals(space.getUserId())){
                return ADMIN_PERMISSIONS;
            }else{
                return Collections.singletonList(SpaceUserPermissionConstant.PICTURE_VIEW);
            }
        }else{
            //团队空间
            spaceUser = spaceUserService.lambdaQuery()
                    .eq(SpaceUser::getSpaceId, spaceId)
                    .eq(SpaceUser::getUserId, userId)
                    .one();
            if(spaceUser == null){
                return new ArrayList<>();
            }
            return spaceAuthManager.getPermissionByRole(spaceUser.getSpaceRole());
        }
    }

    @Override
    public List<String> getRoleList(Object o, String s) {
        return List.of();
    }

    /**
     * 从请求中获取上下文对象
     */
    private SpaceUserAuthContext getSpaceUserAuthContext() {
        HttpServletRequest request
                = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String content_type = request.getHeader(Header.CONTENT_TYPE.getValue());
        SpaceUserAuthContext authContext;
        if (ContentType.JSON.getValue().equals(content_type)) {
            String body = ServletUtil.getBody(request);
            authContext = JSONUtil.toBean(body, SpaceUserAuthContext.class);
        }else{
            Map<String, String> paramMap = ServletUtil.getParamMap(request);
            authContext = BeanUtil.toBean(paramMap, SpaceUserAuthContext.class);
        }
        Long id = authContext.getId();
        //根据请求路径推断id的含义
        if(ObjUtil.isNotNull(id)){
            String uri = request.getRequestURI();
            String partUri = uri.replace(contextPath + "/", "");
            String moduleName = StrUtil.subBefore(partUri, "/", false);

            switch (moduleName){
                case "picture":
                    authContext.setPictureId(id);
                    break;
                case "space":
                    authContext.setSpaceId(id);
                    break;
                case "spaceUser":
                    authContext.setSpaceUserId(id);
                    break;
                default:
                    break;
            }
        }
        return authContext;
    }

    private boolean isAllFieldNull(Object obj) {
        if(obj == null){
            return true;
        }
        //获取所有字段并判断是否所有的字段都为空
        return Arrays.stream(ReflectUtil.getFields(obj.getClass()))
                .map(field -> ReflectUtil.getFieldValue(obj, field))
                .allMatch(ObjUtil::isEmpty);
    }
}
