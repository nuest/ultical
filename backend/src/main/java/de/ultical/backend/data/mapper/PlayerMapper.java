package de.ultical.backend.data.mapper;

import java.util.List;

import org.apache.ibatis.annotations.*;
import org.apache.ibatis.type.JdbcType;

import de.ultical.backend.model.*;

public interface PlayerMapper extends BaseMapper<Player> {

	public static final String SELECT_STMT = "SELECT id, version, first_name as firstName, last_name as lastName, email, gender, birth_date as birthDate, dfv_number as dfvNumber, is_registered FROM PLAYER p LEFT JOIN DFV_PLAYER ON p.id = DFV_PLAYER.player_id LEFT JOIN UNREGISTERED_PLAYER ON p.id = UNREGISTERED_PLAYER.player_id";

	@Override
	@InsertProvider(type = PlayerInsertProvider.class, method = "getInsertSql")
	@Options(keyProperty = "id", useGeneratedKeys = true)
	Integer insert(Player entity);

	@Override
	@Select({
			SELECT_STMT,"WHERE id=#{id}" })
	@TypeDiscriminator(column = "is_registered", javaType = Boolean.class, jdbcType = JdbcType.BOOLEAN, cases = {
			@Case(type = DfvPlayer.class, value = "true"), @Case(type = UnregisteredPlayer.class, value = "false") })
	Player get(int id);
	
	@Override
	@Update({"UPDATE PLAYER SET version=version+1, first_name=#{firstName}, last_name=#{lastName},",
		"gender=#{gender} WHERE id=#{id} AND version=#{version}"})
	Integer update(Player entity);
	
	@Override
	@Delete("DELETE FROM PLAYER WHERE id=#{id}")
	void delete(Player entity);
	
	@Override
	@Select(SELECT_STMT)
	@TypeDiscriminator(column = "is_registered", javaType = Boolean.class, jdbcType = JdbcType.BOOLEAN, cases = {
			@Case(type = DfvPlayer.class, value = "true"), @Case(type = UnregisteredPlayer.class, value = "false") })
	List<Player> getAll();
}