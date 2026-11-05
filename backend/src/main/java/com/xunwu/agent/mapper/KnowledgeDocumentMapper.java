package com.xunwu.agent.mapper;

import com.xunwu.agent.entity.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 知识库文档 Mapper
 *
 * @author xunwu
 */
@Mapper
public interface KnowledgeDocumentMapper {

    /** 新增文档，返回自增主键 */
    int insert(KnowledgeDocument document);

    /** 更新文档 */
    int update(KnowledgeDocument document);

    /** 根据 ID 查询 */
    KnowledgeDocument selectById(@Param("id") Long id);

    /** 查询全部文档 */
    List<KnowledgeDocument> selectAll();

    /** 根据 ID 删除 */
    int deleteById(@Param("id") Long id);

    /** 更新分段数量 */
    int updateSegmentCount(@Param("id") Long id, @Param("segmentCount") int segmentCount);
}
