package com.yupi.yupicturebackend.model.enums;


import cn.hutool.core.util.StrUtil;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public enum SpaceRoleEnum {
    VIEWER("浏览者","viewer"),
    EDITOR("编辑者","editor"),
    ADMIN("管理员","admin");


    private final String text;
    private final String value;

    SpaceRoleEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据value获取枚举值
     */
    public static SpaceRoleEnum getEnumByValue(String value) {
        if(StrUtil.isEmpty( value)){
            return null;
        }
        for (SpaceRoleEnum anEnum : SpaceRoleEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }

    /**
     * 获取所有枚举的text列表
     */
    public static List<String> getAllText() {
        return Arrays.stream(SpaceRoleEnum.values())
                .map(anEnum -> anEnum.text)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有枚举的text列表
     */
    public static List<String> getAllValue() {
        return Arrays.stream(SpaceRoleEnum.values())
                .map(anEnum -> anEnum.value)
                .collect(Collectors.toList());
    }
}
