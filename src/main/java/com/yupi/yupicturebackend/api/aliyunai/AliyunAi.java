package com.yupi.yupicturebackend.api.aliyunai;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.yupi.yupicturebackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.yupi.yupicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.yupi.yupicturebackend.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AliyunAi {

    private static final String CREATE_OUT_PAINTING_TASK_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/image2image/out-painting";

    private static final String GET_OUT_PAINTING_TASK_URL = "https://dashscope.aliyuncs.com/api/v1/tasks/%s";

    @Value("${aliYunAi.apiKey}")
    private String API_KEY;

    /**
     * 创建任务
     */
    public CreateOutPaintingTaskResponse createOutPaintingTask(CreateOutPaintingTaskRequest request){
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR,"ai扩图请求参数为空");
        HttpRequest httpRequest = HttpRequest.post(CREATE_OUT_PAINTING_TASK_URL)
                .header("Authorization", "Bearer " + API_KEY)
                .header("X-DashScope-Async", "enable")
                .header("Content-Type", "application/json")
                .body(JSONUtil.toJsonStr(request));
        try(HttpResponse response = httpRequest.execute()){
            if(!response.isOk()){
                log.error("创建任务失败");
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"AI扩图失败");
            }
            String body = response.body();
            CreateOutPaintingTaskResponse taskResponse = JSONUtil.toBean(body, CreateOutPaintingTaskResponse.class);
            String code = taskResponse.getCode();
            if(StrUtil.isNotBlank(code)){
                String message = taskResponse.getMessage();
                log.error("创建任务失败", message);
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"AI扩图接口响应异常");
            }
            return taskResponse;
        }
    }

    /**
     * 获取任务结果
     */
    public GetOutPaintingTaskResponse getOutPaintingTask(String taskId){
        ThrowUtils.throwIf(StrUtil.isBlank(taskId), ErrorCode.PARAMS_ERROR,"任务id不能为空");

        HttpRequest httpRequest = HttpRequest.get(String.format(GET_OUT_PAINTING_TASK_URL, taskId))
                .header("Authorization", "Bearer " + API_KEY)
                .header("Content-Type", "application/json");
        try(HttpResponse response = httpRequest.execute()){
            if(!response.isOk()){
                log.error("获取任务结果失败");
                throw new BusinessException(ErrorCode.OPERATION_ERROR,"获取任务结果失败");
            }
            String body = response.body();
            return JSONUtil.toBean(body, GetOutPaintingTaskResponse.class);
        }
    }
}
