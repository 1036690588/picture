package com.yupi.yupicturebackend.aop;

import com.yupi.yupicturebackend.annotation.AuthCheck;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.UserRoleEnum;
import com.yupi.yupicturebackend.service.UserService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@Aspect
@Component
public class AuthInterceptor {
    @Resource
    private UserService userService;

    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        String role = authCheck.mustRole();
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        //当前登陆用户
        User loginUser = userService.getLoginUser(request);
        UserRoleEnum roleEnum = UserRoleEnum.getEnumByValue(role);
        //不需要权限，放行
        if(roleEnum == null){
            return joinPoint.proceed();
        }
        //以下为：必须有权限才通过
        //获取当前登录用户具有的权限
        String userRole = loginUser.getUserRole();
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(userRole);
        //没有权限，拒绝
        if(userRoleEnum == null){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        //必须有管理员权限，但用户没有管理员权限，拒绝
        if(UserRoleEnum.ADMIN.getValue().equals(role) && !UserRoleEnum.ADMIN.getValue().equals(userRole)){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        //通过权限校验，放行
        return joinPoint.proceed();
    }
}
