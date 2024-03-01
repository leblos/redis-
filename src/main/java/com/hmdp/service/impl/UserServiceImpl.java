package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.RegexUtils.isCodeInvalid;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    StringRedisTemplate stringRedisTemplate;
    @Override
    public Result fadx(String phone, HttpSession session) {
        //判断电话号码格式是否正确
        if (!isCodeInvalid(phone))
        {
            return Result.fail("电话格式不正确");
        }
        //生成验证码
        String code = RandomUtil.randomNumbers(6);
        //保存验证码
//        session.setAttribute("yzm",code);
        //其他业务可能用的到 一个是为了方便查看
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //发送验证码
        System.out.println(code);
        return Result.ok();
    }


    public Result login(LoginFormDTO loginForm, HttpSession session) {
//        //再次验证电话号码是否正确
//        if (!isCodeInvalid(loginForm.getPhone()))
//        {
//            return Result.fail("电话格式不正确");
//        }
//        //看 验证码是否和sesion的值是否一样
////        Object s =  session.getAttribute("yzm");
//       String s =  stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+loginForm.getPhone());
//        String yzm = loginForm.getCode();
//        if (!yzm.equals(s))
//        {
//            return Result.fail("验证码错误了傻逼");
//        }
//        //把那到的数据再数据库查看有没有
//      String phone =   loginForm.getPhone();
//       User user= query().eq("phone",phone).one();
//       String token;
//       if ( (user==null))
//       {
//           //插入数据
//            User user1 = new User();
//            user1.setPhone(loginForm.getPhone());
//            user1.setNickName("user_"+RandomUtil.randomString(6));
//            save(user1);
//           //session.setAttribute("user",user1);
//           UserDTO userDTO = new UserDTO();
//           BeanUtils.copyProperties(user1,userDTO);
//           Map map = new HashMap<>();
//           BeanUtils.copyProperties(userDTO,map);
//           token = UUID.randomUUID().toString(true);
//            stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token,map);
//           stringRedisTemplate.expire(LOGIN_USER_KEY+token, LOGIN_USER_TTL, TimeUnit.MINUTES);
//       }else{
//               //session.setAttribute("user",user);
//           UserDTO userDTO = new UserDTO();
//           BeanUtils.copyProperties(user,userDTO);
//           Map map = new HashMap<>();
//           BeanUtils.copyProperties(userDTO,map);
//            token = UUID.randomUUID().toString(true);
//           stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token,map);
//           stringRedisTemplate.expire(LOGIN_USER_KEY+token, LOGIN_USER_TTL, TimeUnit.MINUTES);
//       }
//
//        //返回token
//        return Result.ok(token);
        // 1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.如果不符合，返回错误信息
            return Result.fail("手机号格式错误！");
        }
        // 3.从redis获取验证码并校验
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            // 不一致，报错
            return Result.fail("验证码错误");
        }

        // 4.一致，根据手机号查询用户 select * from tb_user where phone = ?
        User user = query().eq("phone", phone).one();

        // 5.判断用户是否存在
        if (user == null) {
            // 6.不存在，创建新用户并保存
            user = createUserWithPhone(phone);
        }

        // 7.保存用户信息到 redis中
        // 7.1.随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString(true);
        // 7.2.将User对象转为HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        // 7.3.存储
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 7.4.设置token有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 8.返回token
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        // 1.创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        // 2.保存用户
        save(user);
        return user;
    }

    @Override
    public UserDTO me() {
       UserDTO userDTO = UserHolder.getUser();
        return userDTO;
    }
}
