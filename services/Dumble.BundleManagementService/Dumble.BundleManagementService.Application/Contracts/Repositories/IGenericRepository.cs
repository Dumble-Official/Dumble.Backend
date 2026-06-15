using System.Linq.Expressions;
using Dumble.BundleManagementService.Domain.Common;
using Dumble.BundleManagementService.Domain.Specifications;

namespace Dumble.BundleManagementService.Application.Contracts.Repositories;

public interface IGenericRepository<TEntity, TKey>
    where TEntity : AggregateRoot<TKey>
    where TKey : ValueObject
{
    Task<IEnumerable<TEntity>> GetAll(bool asNoTracking = true);
    Task<IReadOnlyList<TEntity>> ListAsync(ISpecification<TEntity, TKey> spec);
    Task<int> Count(Expression<Func<TEntity, bool>> criteria);
    Task<TEntity?> Get(TKey id);
    Task Create(TEntity entity);
    void Update(TEntity entity);
    void Delete(TEntity entity);
    Task<int> CompleteAsync();
}
