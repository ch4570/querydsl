package study.querydsl;


import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;
import study.querydsl.entity.Member;
import static study.querydsl.entity.QMember.*;
import static study.querydsl.entity.QTeam.team;

import study.querydsl.entity.QMember;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;
import java.util.List;

@SpringBootTest
@Transactional
public class QuerydslBasicTest {

    @Autowired
    EntityManager em;

    JPAQueryFactory queryFactory;


    @PersistenceUnit
    EntityManagerFactory emf;

    @BeforeEach
    public void before() {
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
        // member1 찾기
        Member findByJQPL = em.createQuery("select m from Member m where m.username = :username", Member.class)
                .setParameter("username", "member1")
                .getSingleResult();

        assertThat(findByJQPL.getUsername()).isEqualTo("member1");
    }

    @Test
    public void startQuerydsl() {

        // QueryDSL은 기본적으로 preparedStatement의 파라미터 바인딩 방식을 사용한다.
        Member findMember = queryFactory
                .select(member)
                .from(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void search() {
        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1").
                        and(member.age.between(10, 30)))
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void searchAndParam() {

        // AND 조건일경우에는 메서드 체이닝 방식이나 여러 조건을 넣어줄 수 있다(쉼표로 구분).
        Member findMember = queryFactory
                .selectFrom(member)
                .where(
                        member.username.eq("member1"), member.age.eq(10)
                )
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void resultFetchTest() {

        // 전체 조회
        List<Member> fetch = queryFactory
                .selectFrom(member)
                .fetch();
        // 한 건 조회
        Member fetchOne = queryFactory
                .selectFrom(member)
                .fetchOne();

        // limit(1)을 사용한 것과 같다.
        Member fetchFirst = queryFactory
                .selectFrom(member)
                .fetchFirst();

        // 페이징에서 사용이 가능하다.
        QueryResults<Member> result = queryFactory
                .selectFrom(member)
                .fetchResults();

        result.getTotal();
        List<Member> content = result.getResults();

        // count 쿼리로 변경하는 기능을 제공한다.
        long total = queryFactory
                .selectFrom(member)
                .fetchCount();
    }

    /*
    *   회원 정렬 순서
    *   1. 회원 나이 내림차순(desc)
    *   2. 회원 이름 올림차순(asc)
    *   단 2에서 회원 이름이 없으면 마지막에 출력(nulls last)
    * */
    @Test
    public void sort() {
        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(100))
                .orderBy(member.age.desc(), member.username.asc().nullsLast())
                .fetch();

        Member member5 = result.get(0);
        Member member6 = result.get(1);
        Member memberNull = result.get(2);

        assertThat(member5.getUsername()).isEqualTo("member5");
        assertThat(member6.getUsername()).isEqualTo("member6");
        assertThat(memberNull.getUsername()).isNull();
    }

    @Test
    public void paging() {
        List<Member> result = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(0)
                .limit(2)
                .fetch();

        QueryResults<Member> queryResult = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetchResults();

        assertThat(result.size()).isEqualTo(2);
        assertThat(queryResult.getTotal()).isEqualTo(4);
        assertThat(queryResult.getOffset()).isEqualTo(1);
        assertThat(queryResult.getLimit()).isEqualTo(2);
    }

    @Test
    public void aggregation() {

        // 집계 함수 사용 예제
        Tuple tuple = queryFactory
                .select(member.count(),
                        member.age.sum(),
                        member.age.avg(),
                        member.age.max(),
                        member.age.min()
                )
                .from(member)
                .fetchOne();

        assertThat(tuple.get(member.count())).isEqualTo(4);
        assertThat(tuple.get(member.age.sum())).isEqualTo(100);
        assertThat(tuple.get(member.age.avg())).isEqualTo(25);
        assertThat(tuple.get(member.age.max())).isEqualTo(40);
        assertThat(tuple.get(member.age.min())).isEqualTo(10);
    }

    /*
    *  팀의 이름과 각 팀의 평균 연령을 구해라.
    * */
    @Test
    public void groupBy() {
        List<Tuple> result = queryFactory
                .select(team.name, member.age.avg())
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

    /*
    *  팀 A에 소속된 모든 회원을 찾아라.
    * */
    @Test
    public void join() {
        List<Member> result = queryFactory
                .selectFrom(member)
                .join(member.team, team)
                .where(team.name.eq("teamA"))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("member1", "member2");
    }

    /*
    *  세타 조인 - cross join(카티시안 곱)
    *  외부 조인이 불가능 하다 -> join on을 사용하면 외부 조인이 가능하다.
    *  회원의 이름이 팀 이름과 같은 회원 조회
    * */
    @Test
    public void thetaJoin() {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Member> result = queryFactory
                .select(member)
                .from(member, team)
                .where(member.username.eq(team.name))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("teamA", "teamB");
    }

    /*
    *   예) 회원과 팀을 조인하면서, 팀 이름이 teamA인 팀만 조인, 회원은 모두 조회
    *   JPQL : select m, t from Member m left join m.team t on t.name = 'teamA'
    * */
    @Test
    public void join_on_filtering() {

        // on절 사용은 외부 조인을 사용할 경우에만 의미가 있다 -> 내부 조인을 사용할 경우에는 where절 사용으로 필터링 하면된다.
        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(member.team, team) //
                .on(team.name.eq("teamA"))
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /*
    *   연관관계 없는 엔티티 외부 조인
    *   회원의 이름이 팀 이름과 같은 대상 외부 조인
    * */
    @Test
    public void join_on_no_relation() {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(team).on(member.username.eq(team.name))
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @Test
    public void fetchJoinNo() {
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());

        assertThat(loaded).as("페치 조인 미적용").isFalse();


    }

    @Test
    public void fetchJoinUse() {
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .join(member.team, team).fetchJoin()
                .where(member.username.eq("member1"))
                .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());

        assertThat(loaded).as("페치 조인 적용").isTrue();

    }

    /*
    *   나이가 가장 많은 회원 조회
    * */
    @Test
    public void subQuery() {

        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(
                        JPAExpressions.select(memberSub.age.max())
                                .from(memberSub)
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(40);
    }

    /*
     *   나이가 평균 이상인 회원 조회
     * */
    @Test
    public void subQueryGoe() {

        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.goe(
                        JPAExpressions.select(memberSub.age.avg())
                                .from(memberSub)
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(30, 40);
    }

    /*
     *   나이가 in절 범위에 들어가는 회원 조회
     * */
    @Test
    public void subQueryIn() {

        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.in(
                        JPAExpressions.
                                select(memberSub.age)
                                .from(memberSub)
                                .where(memberSub.age.gt(10))
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(20, 30, 40);
    }

    /*
    *   서브쿼리를 select에 사용 -> from 절에는 사용 불가
    *   JPA 표준 스펙은 where 절에만 사용가능하지만 hibernate 구현체를 사용하므로, select & where절 사용 가능하다.
    *   SQL Antipatterns 책을 참고 -> SQL이 복잡하면 애플리케이션에서 풀어내야한다.
    * */
    @Test
    public void selectSubQuery() {
        QMember memberSub = new QMember("memberSub");

        List<Tuple> result = queryFactory
                .select(member.username,
                        JPAExpressions
                                .select(memberSub.age.avg())
                                .from(memberSub)
                        )
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    // 간단한 조건의 Simple Case 문
    @Test
    public void basicCase() {
        List<String> result = queryFactory
                .select(member.age
                        .when(10).then("열살")
                        .when(20).then("스무살")
                        .otherwise("기타"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    // 복잡한 분기처리는 CaseBuilder를 사용할 수 있다.
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

    // 특정 상수를 사용하려면 사용이 가능하다 -> JPQL에는 표시되지 않는다.
    @Test
    public void constant() {
        List<Tuple> result = queryFactory
                .select(member.username, Expressions.constant("A"))
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    // 문자열 연결의 경우 타입이 맞지 않으면, stringValue를 호출하여 사용해야한다.
    @Test
    public void concat() {
        List<String> result = queryFactory
                .select(member.username.concat("_").concat(member.age.stringValue()))
                .from(member)
                .where(member.username.eq("member1"))
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }


    @Test
    public void simpleProjection() {
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
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

    // 순수 JPA에서 DTO 조회할 때는 new 명령어를 사용해야하고, DTO의 package 이름을 다 적어줘야 해서 지저분하다.
    @Test
    public void findDtoByJPQL() {
        List<MemberDto> result = em.createQuery("select new study.querydsl.dto.MemberDto(m.username, m.age) from Member m"
                , MemberDto.class)
                .getResultList();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /*
    *  Setter를 이용해서 Injection을 받을 수 있다 -> 첫 번째 방법
    *  기본 생성자가 없을 경우 에러가 난다 -> 기본 생성자로 객체를 생성하고 값을 setter로 넣어주기 때문에 필요하다.
    * */
    @Test
    public void findDtoBySetter() {
        List<MemberDto> result = queryFactory
                .select(Projections.bean(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /*
     *  Getter & Setter 없이 필드에 바로 값을 바인딩 하는 방법 -> 두 번째 방법
     * */
    @Test
    public void findDtoByField() {
        List<MemberDto> result = queryFactory
                .select(Projections.fields(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /*
     *  생성자를 사용해서 DTO를 반환받는 방법 -> 두 번째 방법
     * */
    @Test
    public void findDtoByConstructor() {
        List<MemberDto> result = queryFactory
                .select(Projections.constructor(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /*
    *  필드 이름이 매칭되지 않을 경우에는 null로 값이 매핑된다.
    *  이럴때는 as 메서드를 이용해서 별칭으로 맞춰주면 값이 정상적으로 매핑된다.
    * */
    @Test
    public void findUserDtoByFields() {

        QMember memberSub = new QMember("memberSub");

        List<UserDto> result = queryFactory
                .select(Projections.fields(UserDto.class,
                        member.username.as("name"),
                                ExpressionUtils.as(JPAExpressions
                                        .select(memberSub.age.max())
                                        .from(memberSub), "age")
                ))
                .from(member)
                .fetch();

        for (UserDto userDto : result) {
            System.out.println("memberDto = " + userDto);
        }
    }

    /*
    *  @QueryProjection을 사용한 방법
    *  @QueryProjection을 사용하면 컴파일 타임에 오류를 알 수 있다.
    *  constructor 메서드를 이용하면, 런타임에 문제가 생긴다.
    *  @QueryProjection을 사용하면 DTO가 QueryDSL에 의존성이 생긴다.
    * */
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

    /*
    *  동적 쿼리 해결 - BooleanBuilder
    * */
    @Test
    public void dynamicQuery_BooleanBuilder() {
       String usernameParam = "member1";
       Integer ageParam = 10;

       List<Member> result = searchMember1(usernameParam, ageParam);

       assertThat(result.size()).isEqualTo(1);
    }

    /*
    *  동적 쿼리 해결 - where 다중 파라미터 사용
    * */
    @Test
    public void dynamicQuery_WhereParam() {
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember2(usernameParam, ageParam);

        assertThat(result.size()).isEqualTo(1);
    }

    /*
    *  queryDSL을 이용한 벌크성 연산
    * */
    @Test
    public void bulkUpdate() {

        // member1 = 10 -> DB 비회원
        // member2 = 20 -> DB 비회원
        // member3 = 30 -> DB member3
        // member4 = 40 -> DB member4


        /*
        *   벌크 연산은 영속성 컨텍스트와 현재 DB의 데이터가 다르게 되는 원인이 된다 -> 벌크 연산을 실행하면,
        *   영속성 컨텍스트를 flush & clear 해줘야한다.
        * */
        long count = queryFactory
                .update(member)
                .set(member.username, "비회원")
                .where(member.age.lt(28))
                .execute();

        em.flush();
        em.clear();

        List<Member> result = queryFactory
                .selectFrom(member)
                        .fetch();

        for (Member member : result) {
            System.out.println("member = " + member);
        }

        assertThat(count).isEqualTo(2);
    }


    /*
    *   숫자를 연산할 수 있다 -> minus는 없기 때문에 add 메서드 안에 음수를 인자로 주면 된다.
    * */
    @Test
    public void bulkAdd() {
        long count = queryFactory
                .update(member)
                .set(member.age, member.age.add(1))
                .execute();
    }

    /*
    *   벌크성 삭제 연산도 가능하다.
    * */
    @Test
    public void bulkDelete() {
        long count = queryFactory
                .delete(member)
                .where(member.age.gt(18))
                .execute();
    }

    /*
    *  replace 함수 사용
    * */
    @Test
    public void sqlFunction() {

        // Member의 이름중 member를 M으로 치환하여 조회
        List<String> result = queryFactory
                .select(Expressions.stringTemplate(
                        "function('replace', {0}, {1}, {2})",
                        member.username, "member", "M"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    /*
    *   lower 함수 사용
    *   ANSI 표준에 있는 함수들은 대부분 제공이 되고있다.
    * */
    @Test
    public void sqlFunction2() {
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
//                .where(member.username.eq(Expressions.stringTemplate("function('lower', {0})", member.username)))
                .where(member.username.eq(member.username.lower()))
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    /*
    *  where 절의 다중 파라미터를 사용하면
    *  queryDSL에서는 조건절에 null이 들어가면 조건을 무시하고 진행한다.
    *  BooleanBuilder 보다 훨씬 직관적이고 코드가 보기 좋다.
    * */
    private List<Member> searchMember2(String usernameCond, Integer ageCond) {
        return queryFactory
                .selectFrom(member)
                .where(allEq(usernameCond, ageCond))
                .fetch();
    }

    /*
    *  BooleanExpression을 사용하면 여러 조건들을 합성(Composition)에서 해결이 가능하다.
    * */
    private BooleanExpression usernameEq(String usernameCond) {
        return usernameCond != null ? member.username.eq(usernameCond) : null;
    }

    private BooleanExpression ageEq(Integer ageCond) {
        return ageCond != null ? member.age.eq(ageCond) : null;
    }

    /*
    *  광고 상태 isValid, 날짜가 IN: isServiceable
    *  다양한 조건을 가진 쿼리가 필요할때 BooleanExpression을 통한 합성을 이용하면
    *  재사용도 가능하고, 코드가 직관적이라 보기도 편해진다.
    * */


    private BooleanExpression allEq(String usernameCond, Integer ageCond) {
        return usernameEq(usernameCond).and(ageEq(ageCond));
    }


    private List<Member> searchMember1(String usernameCond, Integer ageCond) {

        /*
        * null을 허용하지 않아야 할 경우에는 초기 값을 넣어 줄 수 있다.
        * new BooleanBuilder(member.name.eq(usernameCond);
        * */
        BooleanBuilder builder = new BooleanBuilder();

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

}
