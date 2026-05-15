using System.Linq.Expressions;
using Dumble.BundleManagementService.Domain.Common;

namespace Dumble.BundleManagementService.Application.Contracts.Repositories;

public interface IGenericRepository<TEntity, TKey>
    where TEntity : AggregateRoot<TKey>
    where TKey : ValueObject
{
    Task<IEnumerable<TEntity>> GetAll(bool asNoTracking = true);
    Task<int> Count(Expression<Func<TEntity, bool>> criteria);
    Task<TEntity?> Get(TKey id);
    Task Create(TEntity entity);
    void Update(TEntity entity);
    void Delete(TEntity entity);
    Task<int> CompleteAsync();
}
