package study.querydsl;

import static com.querydsl.jpa.JPAExpressions.*;
import static org.assertj.core.api.Assertions.*;
import static study.querydsl.entity.QMember.*;
import static study.querydsl.entity.QTeam.*;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;

import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.Team;

@SpringBootTest
@Transactional
public class QuerydslBasicTest {
	@Autowired
	private EntityManager em;

	JPAQueryFactory queryFactory;

	@BeforeEach
	void setUp() {
		queryFactory = new JPAQueryFactory(em);

		Team teamA = new Team("teamA");
		Team teamB = new Team("teamB");

		em.persist(teamA);
		em.persist(teamB);

		Member member1 = new Member("member1", 10, teamA);
		Member member2 = new Member("member2", 20, teamA);
		Member member3 = new Member("member3", 30, teamB);
		Member member4 = new Member("member4", 40, teamB);
		em.persist(member1);
		em.persist(member2);
		em.persist(member3);
		em.persist(member4);
	}

	@Test
	public void startJPQL() {
		// member1을 찾아라
		String qlString = "select m from Member m where m.username = :username";

		Member findMember = em.createQuery(qlString, Member.class)
			.setParameter("username", "member1")
			.getSingleResult();

		assertThat(findMember.getUsername()).isEqualTo("member1");

	}

	@Test
	public void startQuerydsl() {
		// QMember m = new QMember("m"); 여기서 "m"은 jpql의 alias이고 같은 테이블을 조인해야하는 경우가 생기면 그때 선언해서 사용
		// QMember m = QMember.member;
		// 위의 두개 다 사용가능하지만 static import가 가장 깔끔함

		Member findMember = queryFactory
			.select(member)
			.from(member)
			.where(member.username.eq("member1"))
			.fetchOne();

		assertThat(findMember.getUsername()).isEqualTo("member1");
	}

	@Test
	public void search() {
		Member findMember = queryFactory.selectFrom(member)
			.where(member.username.eq("member1")
				.and(member.age.eq(10)))
			.fetchOne();

		assertThat(findMember.getUsername()).isEqualTo("member1");
	}

	@Test
	public void searchAndParam() {
		Member findMember = queryFactory.selectFrom(member)
			.where(
				member.username.eq("member1"),
				member.age.eq(10)
			)
			.fetchOne();

		assertThat(findMember.getUsername()).isEqualTo("member1");
	}

	@Test
	public void resulFetch() {
		// List<Member> fetch = queryFactory.selectFrom(member).fetch();

		// Member fetchOne = queryFactory.selectFrom(member).fetchOne();

		// Member fetchFirst = queryFactory.selectFrom(member).fetchFirst();

		// QueryResults<Member> results = queryFactory.selectFrom(member).fetchResults();

		//아래 두개는 deprecated 됨 fetch()로 대체 가능
		// List<Member> content = results.getResults();

		// long total = queryFactory.selectFrom(member).fetchCount();

	}

	/**
	 * 회원 정렬 순서
	 * 1. 회원 나이 내림차순(desc)
	 * 2. 회원 이름 올림차순(asc)
	 * 단 2에서 회원 이름이 없으면 마지막에 출력(nulls last)
	 */
	@Test
	public void sort() {
		em.persist(new Member(null, 100));
		em.persist(new Member("member5", 100));
		em.persist(new Member("member6", 100));

		List<Member> result = queryFactory
			.selectFrom(member)
			.where(member.age.eq(100))
			.orderBy(
				member.age.desc(),
				member.username.asc().nullsLast()
			).fetch();

		Member member5 = result.get(0);
		Member member6 = result.get(1);
		Member memberNull = result.get(2);

		assertThat(member5.getUsername()).isEqualTo("member5");
		assertThat(member6.getUsername()).isEqualTo("member6");
		assertThat(memberNull.getUsername()).isNull();

	}

	@Test
	public void paging1() {
		List<Member> result = queryFactory.selectFrom(member)
			.orderBy(member.username.desc())
			.offset(1)
			.limit(2)
			.fetch();

		assertThat(result).hasSize(2);
	}

	@Test
	public void paging2() {
		QueryResults<Member> queryResults = queryFactory.selectFrom(member)
			.orderBy(member.username.desc())
			.offset(1)
			.limit(2)
			.fetchResults();

		assertThat(queryResults.getResults()).hasSize(2);
		assertThat(queryResults.getTotal()).isEqualTo(4);
		assertThat(queryResults.getOffset()).isEqualTo(1);
		assertThat(queryResults.getLimit()).isEqualTo(2);
	}

	@Test
	public void aggregation() {
		List<Tuple> result = queryFactory.select(
				member.count(), member.age.sum(), member.age.avg(),
				member.age.max(), member.age.min())
			.from(member).fetch();

		Tuple tuple = result.get(0);
		assertThat(tuple.get(member.count())).isEqualTo(4);
		assertThat(tuple.get(member.age.sum())).isEqualTo(100);
		assertThat(tuple.get(member.age.avg())).isEqualTo(25);
		assertThat(tuple.get(member.age.max())).isEqualTo(40);
		assertThat(tuple.get(member.age.min())).isEqualTo(10);

	}

	/**
	 * 팀의 이름과 각 팀의 평균 연령을 구해라
	 */
	@Test
	void group() throws Exception {
		List<Tuple> result = queryFactory.select(team.name, member.age.avg())
			.from(member)
			.join(member.team, team)
			.groupBy(team.name)
			.fetch();

		Tuple teamA = result.get(0);
		Tuple teamB = result.get(1);

		assertThat(teamA.get(team.name)).isEqualTo("teamA");
		assertThat(teamA.get(member.age.avg())).isEqualTo(15);

		assertThat(teamB.get(team.name)).isEqualTo("teamB");
		assertThat(teamB.get(member.age.avg())).isEqualTo(35);

	}

	/**
	 * 팀 A에 소속된 모든 회원을 찾아라
	 */
	@Test
	public void join() {
		List<Member> result = queryFactory.selectFrom(member)
			.join(member.team, team)
			.where(team.name.eq("teamA"))
			.fetch();

		assertThat(result).extracting("username").containsExactly("member1", "member2");
	}

	/**
	 * 세타 조인
	 * 연관관계가 없어도 조인하기
	 * 회원의 이름이 팀이름과 같은 회원 조회
	 * 세타 조인이라고 한다.
	 * 아래와 같은 방식은 외부조인이 불가능하다.(그러나 on을 사용하면 가능)
	 */
	@Test
	public void theta_join() {
		em.persist(new Member("teamA"));
		em.persist(new Member("teamB"));

		List<Member> result = queryFactory.select(member)
			.from(member, team)
			.where(member.username.eq(team.name))
			.fetch();

		assertThat(result).extracting("username").containsExactly("teamA", "teamB");
	}

	/**
	 * 예) 회원과 팀을 조인하면서, 팀 이름이 teamA인 팀만 조인, 회원은 모두 조회
	 * JQPL : select m, t from Member m left join m.team t on t.name = 'teamA'
	 *
	 * 이것은 외부조인이 필요한 경우에만 사용하도록!
	 */
	@Test
	public void join_on_filtering() {
		List<Tuple> result = queryFactory.select(member, team)
			.from(member)
			.leftJoin(member.team, team)
			.on(team.name.eq("teamA"))
			.fetch();

		for (Tuple tuple : result) {
			System.out.println("tuple = " + tuple);
		}
	}

	/**
	 * 연관관계가 없는 엔티티를 외부 조인
	 * 회원의 이름 팀 이름과 같은 대상 외부 조인
	 */
	@Test
	public void join_on_no_relation() {
		em.persist(new Member("teamA"));
		em.persist(new Member("teamB"));
		em.persist(new Member("teamC"));

		List<Tuple> result = queryFactory.select(member, team)
			.from(member)
			.leftJoin(team)
			.on(member.username.eq(team.name))
			.fetch();

		for (Tuple tuple : result) {
			System.out.println("tuple = " + tuple);
		}
	}

	@PersistenceUnit
	EntityManagerFactory emf;

	@Test
	public void fetchJoinNo() {
		em.flush();
		em.clear();

		Member findMember = queryFactory.selectFrom(QMember.member)
			.where(QMember.member.username.eq("member1"))
			.fetchOne();

		boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());

		assertThat(loaded).isFalse();
	}

	@Test
	public void fetchJoinUse() {
		em.flush();
		em.clear();

		Member findMember = queryFactory
			.selectFrom(QMember.member)
			.join(member.team, team).fetchJoin()
			.where(QMember.member.username.eq("member1"))
			.fetchOne();

		boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());

		assertThat(loaded).isTrue();
	}

	/**
	 * 나이가 가장 많은 회원을 조회
	 */
	@Test
	public void subQuery() {
		QMember memberSub = new QMember("memberSub");

		List<Member> result = queryFactory.selectFrom(member)
			.where(member.age.eq(
				select(memberSub.age.max())
					.from(memberSub)
			))
			.fetch();

		assertThat(result).extracting("age").containsExactly(40);
	}

	/**
	 * 나이가 평균 이상인 회원을 조회
	 */
	@Test
	public void subQueryGoe() {
		QMember memberSub = new QMember("memberSub");

		List<Member> result = queryFactory.selectFrom(member)
			.where(member.age.goe(
				select(memberSub.age.avg())
					.from(memberSub)
			))
			.fetch();

		assertThat(result).extracting("age").containsExactly(30, 40);
	}

	/**
	 * 나이가 가장 많은 회원을 조회
	 */
	@Test
	public void subQueryIn() {
		QMember memberSub = new QMember("memberSub");

		List<Member> result = queryFactory.selectFrom(member)
			.where(member.age.in(
				select(memberSub.age)
					.from(memberSub)
					.where(memberSub.age.gt(10))
			))
			.fetch();

		assertThat(result).extracting("age").containsExactly(20, 30, 40);
	}

	@Test
	public void selectSubQuery() {
		QMember memberSub = new QMember("memberSub");
		List<Tuple> result = queryFactory
			.select(member.username,
				select(memberSub.age.avg())
					.from(memberSub))
			.from(member)
			.fetch();

		for (Tuple tuple : result) {
			System.out.println("tuple = " + tuple);
		}
	}

	@Test
	public void basicCase() {
		List<String> result = queryFactory
			.select(member.age
				.when(10).then("열상")
				.when(20).then("스무살")
				.otherwise("기타"))
			.from(member)
			.fetch();

		for (String s : result) {
			System.out.println("s = " + s);
		}
	}

	@Test
	public void complexCase() {
		List<String> result = queryFactory
			.select(new CaseBuilder()
				.when(member.age.between(0, 20)).then("0~20살")
				.when(member.age.between(21, 30)).then("21~30살")
				.otherwise("기타"))
			.from(member)
			.fetch();

		for (String s : result) {
			System.out.println("s = " + s);
		}
	}

	@Test
	public void constant() {
		List<Tuple> result = queryFactory
			.select(member.username, Expressions.constant("A"))
			.from(member)
			.fetch();

		for (Tuple tuple : result) {
			System.out.println("tuple : " + tuple);
		}
	}

	@Test
	public void concat() {
		//{username}_age
		List<String> fetch = queryFactory
			.select(member.username.concat("_").concat(member.age.stringValue()))
			.from(member)
			.where(member.username.eq("member1"))
			.fetch();

		for (String s : fetch) {
			System.out.println("s = " + s);
		}
	}

	@Test
	public void simpleProjection() {
		List<Member> result = queryFactory
			.select(member)
			.from(member)
			.fetch();

		System.out.println("result = " + result);
	}

	@Test
	public void tupleProjection() {
		List<Tuple> result = queryFactory
			.select(member.username, member.age)
			.from(member)
			.fetch();

		for (Tuple tuple : result) {
			String username = tuple.get(member.username);
			Integer age = tuple.get(member.age);
			System.out.println("username = " + username);
			System.out.println("age = " + age);
		}
	}

	@Test
	public void findDtoByJPQL() {
		List<MemberDto> result = em.createQuery(
			"select new study.querydsl.dto.MemberDto(m.username, m.age) from Member m",
			MemberDto.class).getResultList();

		for (MemberDto memberDto : result) {
			System.out.println("memberDto = " + memberDto);
		}
	}

	@Test
	public void findDtoBySetter() {
		List<MemberDto> result = queryFactory
			.select(Projections.bean(MemberDto.class, member.username, member.age))
			.from(member)
			.fetch();

		for (MemberDto memberDto : result) {
			System.out.println("memberDto = " + memberDto);
		}
	}

	@Test
	public void findDtoByField() {
		List<MemberDto> result = queryFactory
			.select(Projections.fields(MemberDto.class, member.username, member.age))
			.from(member)
			.fetch();

		for (MemberDto memberDto : result) {
			System.out.println("memberDto = " + memberDto);
		}
	}

	@Test
	public void findDtoByConstructor() {
		List<MemberDto> result = queryFactory
			.select(Projections.constructor(MemberDto.class, member.username, member.age))
			.from(member)
			.fetch();

		for (MemberDto memberDto : result) {
			System.out.println("memberDto = " + memberDto);
		}
	}

	//이 테스트는 필드명이 다르기 때문에 username에 null이 들어간다.
	//그때는 .as
	@Test
	public void findUserDto() {
		List<UserDto> result = queryFactory
			.select(Projections.fields(UserDto.class,
				member.username.as("name"), member.age))
			.from(member)
			.fetch();

		for (UserDto userDto : result) {
			System.out.println("memberDto = " + userDto);
		}
	}

	//서브쿼리의 결과를 매칭시키기
	@Test
	public void findUserDtoWithExpressions() {
		QMember memberSub = new QMember("memberSub");
		List<UserDto> result = queryFactory
			.select(Projections.fields(UserDto.class,
					member.username.as("name"),
					ExpressionUtils.as(
						JPAExpressions
							.select(memberSub.age.max())
							.from(memberSub), "age")
				)
			)
			.from(member)
			.fetch();

		for (UserDto userDto : result) {
			System.out.println("memberDto = " + userDto);
		}
	}

	@Test
	public void findUserDtoByConstructor() {
		List<UserDto> result = queryFactory
			.select(Projections.constructor(UserDto.class, member.username, member.age))
			.from(member)
			.fetch();

		for (UserDto userDto : result) {
			System.out.println("memberDto = " + userDto);
		}
	}

	@Test
	public void findDtoByQueryProjection() {
		List<MemberDto> result = queryFactory
			.select(new QMemberDto(member.username, member.age))
			.from(member)
			.fetch();

		for (MemberDto memberDto : result) {
			System.out.println("memberDto = " + memberDto);
		}

	}

	@Test
	public void dynamivQuery_BooleanBuilder() {
		String usernameParam = "member1";
		Integer ageParam = null;

		List<Member> result = searchMember1(usernameParam, ageParam);
		assertThat(result.size()).isEqualTo(1);
	}

	private List<Member> searchMember1(String usernameCond, Integer ageCond) {

		BooleanBuilder builder = new BooleanBuilder(/*member.username.eq(usernameCond)*/);
		if (usernameCond != null) {
			builder.and(member.username.eq(usernameCond));
		}

		if (ageCond != null) {
			builder.and(member.age.eq(ageCond));
		}

		return queryFactory
			.selectFrom(member)
			.where(builder)
			.fetch();
	}

	@Test
	public void dynamicquery_WhereParam() {
		String usernameParam = "member1";
		Integer ageParam = 10;

		List<Member> result = searchMember2(usernameParam, ageParam);
		assertThat(result.size()).isEqualTo(1);
	}

	private List<Member> searchMember2(String usernameCond, Integer ageCond) {
		return queryFactory
			.selectFrom(member)
			.where(usernameEq(usernameCond), ageEq(ageCond))
			//.where(allEq(usernameCond, ageCond))
			.fetch();
	}

	private BooleanExpression usernameEq(String usernameCond) {
		return usernameCond != null ? member.username.eq(usernameCond) : null;
	}

	private BooleanExpression ageEq(Integer ageCond) {
		return ageCond != null ? member.age.eq(ageCond) : null;
	}

	private BooleanExpression allEq(String usernameCond, Integer ageCond) {
		return usernameEq(usernameCond).and(ageEq(ageCond));
	}
}
