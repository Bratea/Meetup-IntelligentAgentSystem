package com.xunwu.agent.mapper;

import com.xunwu.agent.entity.KnowledgeSegment;
import com.xunwu.agent.entity.KnowledgeSearchResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 知识库分段 Mapper（向量检索）
 *
 * @author xunwu
 */
@Mapper
public interface KnowledgeSegmentMapper {

    /** 新增分段 */
    int insert(KnowledgeSegment segment);

    /** 批量新增分段 */
    int batchInsert(@Param("segments") List<KnowledgeSegment> segments);

    /** 根据文档 ID 查询分段 */
    List<KnowledgeSegment> selectByDocumentId(@Param("documentId") Long documentId);

    /** 根据 ID 查询分段 */
    KnowledgeSegment selectById(@Param("id") Long id);

    /** 根据文档 ID 删除分段 */
    int deleteByDocumentId(@Param("documentId") Long documentId);

    /** 根据 ID 删除单个分段 */
    int deleteById(@Param("id") Long id);

    /** 更新分段内容与向量（编辑后重新向量化） */
    int updateContent(@Param("id") Long id,
                      @Param("content") String content,
                      @Param("embedding") String embedding);

    /**
     * 向量相似度检索（余弦距离，越小越相似）
     *
     * @param embedding 查询向量，数组字符串
     * @param limit      Top-K
     */
    List<KnowledgeSearchResult> searchSimilar(@Param("embedding") String embedding,
                                              @Param("limit") int limit);

    /** 统计分段数量 */
    long countByDocumentId(@Param("documentId") Long documentId);
}
