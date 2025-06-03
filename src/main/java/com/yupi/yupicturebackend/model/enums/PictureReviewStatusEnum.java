package com.yupi.yupicturebackend.model.enums;


import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
public enum PictureReviewStatusEnum {

    REVIEWING("审核中", 0),
    PASS("通过", 1),
    REJECT("拒绝", 2);

    private final String text;
    private final Integer value;
    private static final Map<Integer,PictureReviewStatusEnum> PICTURE_REVIEW_STATUS_ENUM_MAP =
            Arrays.stream(PictureReviewStatusEnum.values())
                    .collect(Collectors.toMap(PictureReviewStatusEnum::getValue, e -> e));

    PictureReviewStatusEnum(String text, Integer value) {
        this.text = text;
        this.value = value;
    }


    public static PictureReviewStatusEnum getEnumByValue(Integer value){
        PictureReviewStatusEnum pictureReviewStatusEnum =
                value == null ? null : PICTURE_REVIEW_STATUS_ENUM_MAP.getOrDefault(value,null);
        ThrowUtils.throwIf(pictureReviewStatusEnum == null, ErrorCode.PARAMS_ERROR);
        return pictureReviewStatusEnum;
    }
}
