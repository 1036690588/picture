package com.yupi.yupicturebackend.model.vo.space;

import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.vo.UserVO;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
public class SpaceVO implements Serializable {

    /**
     * id
     */
    private Long id;



    /**
     * 图片名称
     */
    private String spaceName;

    /**
     * 空间级别：0-普通版 1-专业版 2-旗舰版
     */
    private Integer spaceLevel;

    /**
     * 空间图片的最大总大小
     */
    private Long maxSize;

    /**
     * 空间图片的最大数量
     */
    private Long maxCount;

    /**
     * 当前空间下图片的总大小
     */
    private Long totalSize;

    /**
     * 当前空间下的图片数量
     */
    private Long totalCount;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 编辑时间
     */
    private Date editTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 创建用户信息
     */
    private UserVO user;

    /**
     * 空间类型：0-私有 1-团队
     */
    private Integer spaceType;

    /**
     * 权限列表
     */
    private List<String> permissionList = new ArrayList<>();



    private static final long serialVersionUID = 1L;

    /**
     * 封装类转对象
     */
    public static Space voToObj(SpaceVO SpaceVO) {
        if (SpaceVO == null) {
            return null;
        }
        Space Space = new Space();
        BeanUtils.copyProperties(SpaceVO, Space);
        return Space;
    }

    /**
     * 对象转封装类
     */
    public static SpaceVO objToVo(Space Space) {
        if (Space == null) {
            return null;
        }
        SpaceVO SpaceVO = new SpaceVO();
        BeanUtils.copyProperties(Space, SpaceVO);
        return SpaceVO;
    }
}
