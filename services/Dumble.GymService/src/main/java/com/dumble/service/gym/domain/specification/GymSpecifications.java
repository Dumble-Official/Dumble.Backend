package com.dumble.service.gym.domain.specification;

import com.dumble.service.gym.domain.entity.Gym;
import com.dumble.service.gym.domain.enumuration.GenderType;
import com.dumble.service.gym.domain.enumuration.GymStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Predicate;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

public class GymSpecifications {

    public static Specification<Gym> hasName(String name){
        return new Specification<>(){
        @Override
        public Predicate toPredicate(Root<Gym> root, CriteriaQuery<?> query, CriteriaBuilder cb) {

            if(name == null || name.isEmpty()){
                return null;
            }
            return cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%");
        }
        };
    }

    public static Specification<Gym> hasGender(GenderType gender){
        return new Specification<Gym>(){
            @Override
            public Predicate toPredicate(Root<Gym> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
                if(gender == null){
                    return null;
                }
                return cb.equal(root.get("gender"), gender);
            }
        };
    }

    public static Specification<Gym> hasStatus(GymStatus status){
        return new Specification<Gym>() {
            @Override
            public Predicate toPredicate(Root<Gym> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
                if(status == null){
                    return null;
                }
                return cb.equal(root.get("status"), status);
            }
        };
    }

    public static Specification<Gym> isVerified(Boolean verified){
        return new Specification<Gym>() {
            @Override
            public Predicate toPredicate(Root<Gym> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
                if(verified == null){
                    return null;
                }
                return cb.equal(root.get("isVerified"), verified);
            }
        };
    }
}

