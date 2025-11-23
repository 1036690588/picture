package com.yupi.yupicturebackend.manager.websocket;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.yupi.yupicturebackend.manager.websocket.disruptor.PictureEditEventProducer;
import com.yupi.yupicturebackend.manager.websocket.model.PictureEditActionEnum;
import com.yupi.yupicturebackend.manager.websocket.model.PictureEditMessageTypeEnum;
import com.yupi.yupicturebackend.manager.websocket.model.PictureEditRequestMessage;
import com.yupi.yupicturebackend.manager.websocket.model.PictureEditResponseMessage;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.service.UserService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.Resource;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PictureEditHandler extends TextWebSocketHandler {

    @Resource
    private UserService userService;

    @Resource
    private PictureEditEventProducer pictureEditEventProducer;

    //每张图片的编辑状态， key：pictureId value:正在编辑的用户ID
    private final ConcurrentHashMap<Long,Long> pictureEditUsers = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Long, Set<WebSocketSession>> pictureSessions = new ConcurrentHashMap<>();


    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);
        //保存会话到集合中
        User user = (User)session.getAttributes().get("user");
        Long pictureId = (Long)session.getAttributes().get("pictureId");
        pictureSessions.putIfAbsent(pictureId, ConcurrentHashMap.newKeySet());
        pictureSessions.get(pictureId).add(session);
        //构造相应
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setUser(userService.getUserVO(user));
        String message = String.format("%s加入该图片编辑页面", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        //广播给同一张图片的用户
        broadcastToPicture(pictureId, pictureEditResponseMessage);

    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        super.handleTextMessage(session, message);
        //将消息解析为PictureEditMessage
        PictureEditRequestMessage pictureEditRequestMessage = JSONUtil.toBean(message.getPayload(), PictureEditRequestMessage.class);
//        String type = pictureEditRequestMessage.getType();
//        PictureEditMessageTypeEnum editMessageTypeEnum = PictureEditMessageTypeEnum.getEnumByValue(type);

        //从Session属性中获取参数
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");

        pictureEditEventProducer.publishEvent(pictureEditRequestMessage, session, user, pictureId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        super.afterConnectionClosed(session, status);
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        handleExitEditMessage(null, session, user, pictureId);

        //删除会话
        Set<WebSocketSession> webSocketSessions = pictureSessions.get(pictureId);
        if(webSocketSessions != null){
            webSocketSessions.remove(session);
            if(webSocketSessions.isEmpty()){
                pictureSessions.remove(pictureId);
            }
        }

        //构造响应
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setUser(userService.getUserVO(user));
        pictureEditResponseMessage.setMessage(String.format("%s离开编辑界面", user.getUserName()));
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        broadcastToPicture(pictureId, pictureEditResponseMessage);

    }

    public void handleEnterEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session,
                                        User user, Long pictureId)throws Exception {
        //没有其他用户正在编辑该图片，才能进入编辑
        if(!pictureEditUsers.contains(pictureId)){
            pictureEditUsers.put(pictureId, user.getId());
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            pictureEditResponseMessage.setMessage(String.format("%s开始编辑图片", user.getUserName()));
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ENTER_EDIT.getValue());
            broadcastToPicture(pictureId, pictureEditResponseMessage);
        }


    }

    public void handleEditActionMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session,
                                         User user, Long pictureId) throws  Exception{
        Long editUserId = pictureEditUsers.get(pictureId);
        String editAction = pictureEditRequestMessage.getEditAction();
        PictureEditActionEnum editActionEnum = PictureEditActionEnum.getEnumByValue(editAction);
        if(editActionEnum == null){
            return ;
        }
        if(editUserId != null && editUserId.equals(user.getId())){
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            pictureEditResponseMessage.setMessage(String.format("%s执行%s", user.getUserName(), editActionEnum.getText()));
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EDIT_ACTION.getValue());
            pictureEditResponseMessage.setEditAction(editAction);
            broadcastToPicture(pictureId, pictureEditResponseMessage,session);
        }
    }

    public void handleExitEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session,
                                       User user, Long pictureId) throws  Exception{
        Long editUser = pictureEditUsers.get(pictureId);
        if(editUser != null && editUser.equals(user.getId())){
            //移除当前用户编辑状态
            pictureEditUsers.remove(pictureId);
            //构造响应
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            pictureEditResponseMessage.setMessage(String.format("%s退出编辑图片", user.getUserName()));
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EXIT_EDIT.getValue());
            broadcastToPicture(pictureId, pictureEditResponseMessage);
        }
    }





    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage,
                                    WebSocketSession excludeSession) throws Exception{
        Set<WebSocketSession> sessions = pictureSessions.get(pictureId);
        if(CollUtil.isNotEmpty(sessions)){
            ObjectMapper objectMapper = new ObjectMapper();
            //配置序列化将Long类型转为字符串 解决精度丢失的问题
            SimpleModule module = new SimpleModule();
            module.addSerializer(Long.class, ToStringSerializer.instance);
            module.addSerializer(Long.TYPE, ToStringSerializer.instance);
            objectMapper.registerModule(module);

            //序列化为JSON字符串
            String message = objectMapper.writeValueAsString(pictureEditResponseMessage);
            TextMessage textMessage = new TextMessage(message);
            for(WebSocketSession webSocketSession : sessions){
                if(webSocketSession != null && webSocketSession.equals(excludeSession)){
                    continue;
                }
                if(webSocketSession.isOpen()){
                    webSocketSession.sendMessage(textMessage);
                }
            }
        }
    }
    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage
                                    ) throws Exception{
        broadcastToPicture(pictureId, pictureEditResponseMessage, null);
    }
}
