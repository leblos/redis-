package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.Map;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;

public class LoginInterceptor implements HandlerInterceptor {

    //这个玩意导入有2个注意点
    StringRedisTemplate stringRedisTemplate;

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        //1.获取session
//        HttpSession session = request.getSession();
//        //2.获取session中的用户
//        Object user = session.getAttribute("user");
        String token = request.getHeader("authorization");
//        if (token.isBlank())
//        {
//            response.setStatus(401);
//            return false;
//        }
        if (token == null || token.isBlank()) {
            response.setStatus(401);
            return false;
        }
       Map map = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY+token);
        if (map.isEmpty())
        {
            response.setStatus(401);
            return false;
        }
       UserDTO userDTO = new UserDTO();
       BeanUtils.copyProperties(map,userDTO);
//        //3.判断用户是否存在
//        if(userDTO == null){
//            //4.不存在，拦截，返回401状态码
//            response.setStatus(401);
//            return false;
//        }
        //

        //5.存在，保存用户信息到Threadlocal
//        UserDTO dto = new UserDTO();
//        BeanUtils.copyProperties(user,dto);
        UserHolder.saveUser(userDTO);
        //6.放行
        return true;
    }
}