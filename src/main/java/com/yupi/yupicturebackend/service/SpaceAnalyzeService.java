package com.yupi.yupicturebackend.service;

import com.yupi.yupicturebackend.model.dto.space.analyze.SpaceAnalyzeRequest;
import com.yupi.yupicturebackend.model.dto.space.analyze.SpaceRankAnalyzeRequest;
import com.yupi.yupicturebackend.model.dto.space.analyze.SpaceUsageAnalyzeRequest;
import com.yupi.yupicturebackend.model.dto.space.analyze.SpaceUserAnalyzeRequest;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.space.analyze.*;

import java.util.List;

public interface SpaceAnalyzeService {

    SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest,
                                                   User loginUser);
    List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(SpaceAnalyzeRequest spaceAnalyzeRequest
            , User loginUser);

    List<SpaceTagAnalyzeResponse> getSpaceTagAnalyze(SpaceAnalyzeRequest spaceAnalyzeRequest,
                                                     User loginUser);

    List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyze(SpaceAnalyzeRequest spaceAnalyzeRequest,
                                                       User loginUser);

    List<SpaceUserAnalyzeResponse> getSpaceUserAnalyze(SpaceUserAnalyzeRequest spaceUserAnalyzeRequest,
                                                       User loginUser);

    List<Space> getSpaceRankAnalyze(SpaceRankAnalyzeRequest spaceRankAnalyzeRequest,
                                    User loginUser);
}
