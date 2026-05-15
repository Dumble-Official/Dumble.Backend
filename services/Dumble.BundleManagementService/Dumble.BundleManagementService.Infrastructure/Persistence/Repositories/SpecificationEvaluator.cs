using Dumble.BundleManagementService.Domain.Common;
using Dumble.BundleManagementService.Domain.Specifications;
using Microsoft.EntityFrameworkCore;

namespace Dumble.BundleManagementService.Infrastructure.Persistence.Repositories;

public static class SpecificationEvaluator
{
    public static IQueryable<TEntity> Evaluate<TEntity, TKey>(this IQueryable<TEntity> query,
        ISpecification<TEntity, TKey> specs)
        where TEntity : AggregateRoot<TKey>
        where TKey : ValueObject
    {
        if (specs.Criteria is not null)
            query = query.Where(specs.Criteria);

        if (specs.Includes is not null)
            query = specs.Includes.Aggregate(query, (current, exp) => current.Include(exp));
        
        if (specs.IsPaginationEnabled)
            query = query.Skip(specs.Skip).Take(specs.Take);
        
        return query;
    }
}