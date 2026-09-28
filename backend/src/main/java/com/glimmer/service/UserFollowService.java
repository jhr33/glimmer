package com.glimmer.service;

import com.glimmer.common.response.PageResult;
import com.glimmer.service.dto.ArticleListVO;
import com.glimmer.service.dto.FollowVO;

/**
 * 交流会作者关注服务：关注（账户+笔名）/ 取消关注 / 备注 / 我的关注文章流
 */
public interface UserFollowService {

    /**
     * 关注作者（关注目标为「账户+笔名」组合，可携带自定义备注）
     *
     * @return 关注记录ID
     */
    Long follow(Long followerId, Long followeeId, String penName, String remark);

    /**
     * 取消关注（按关注记录ID，仅本人操作）
     */
    void unfollow(Long followerId, Long followId);

    /**
     * 修改关注备注（区分不同账户同一笔名）
     */
    void updateRemark(Long followerId, Long followId, String remark);

    /**
     * 我的关注列表（分页，含该笔名下当前可见文章数）
     */
    PageResult<FollowVO> listFollows(Long followerId, int page, int size);

    /**
     * 我的关注文章流：全部已关注「账户+笔名」组合下的公开且审核通过文章，按发布时间倒序分页
     */
    PageResult<ArticleListVO> getFollowingFeed(Long followerId, int page, int size);
}
